package com.ticket.train.service.impl;

import com.ticket.enums.CacheKey;
import com.ticket.service.StockLockService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 库存锁服务实现（混合方案：核心扣减使用Lua脚本保证最高性能，其他操作使用Redisson便捷API）
 */
@Service
public class StockLockServiceImpl implements StockLockService {

    private static final Logger logger = LoggerFactory.getLogger(StockLockServiceImpl.class);

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisUtil redisUtil;

    // Lua脚本路径
    private static final String LUA_DEDUCT_SCRIPT = "lua/deduct_stock.lua";
    private static final String LUA_ROLLBACK_SCRIPT = "lua/rollback_stock.lua";
    private static final String LUA_CONFIRM_SCRIPT = "lua/confirm_stock.lua";

    // 预占库存过期时间（30分钟）
    private static final int LOCKED_EXPIRE_SECONDS = 30 * 60;
    // 库存缓存过期时间（1天）
    private static final int STOCK_EXPIRE_SECONDS = 24 * 60 * 60;
    // 分布式锁等待时间（5秒）
    private static final int LOCK_WAIT_TIME = 5;
    // 分布式锁持有时间（10秒，防止死锁）
    private static final int LOCK_LEASE_TIME = 10;

    @Override
    public boolean tryDeduct(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
        String stockKey = CacheKey.formatTrainStockKey(trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = CacheKey.formatTrainLockedKey(trainId, trainDate, seatType, startStation, endStation);
        
        try {
            // 准备Lua脚本参数
            String[] keys = new String[]{stockKey, lockedKey};
            Object[] args = new Object[]{count, LOCKED_EXPIRE_SECONDS, STOCK_EXPIRE_SECONDS};
            
            // 执行Lua脚本（原子操作，一次网络往返）
            List<Long> result = redisUtil.executeScriptFromResource(LUA_DEDUCT_SCRIPT, Arrays.asList(keys), args);
            
            if (result == null) {
                logger.error("Lua脚本返回null，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
                return false;
            }
            
            if (result.size() >= 2) {
                long status = result.get(0);
                long remainingStock = result.get(1);
                
                if (status == 1) {
                    // 扣减成功
                    logger.info("预扣库存成功（Lua脚本），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 剩余库存={}",
                            trainId, trainDate, seatType, startStation, endStation, count, remainingStock);
                    return true;
                } else if (status == 0) {
                    // 库存不足
                    logger.warn("库存不足（Lua脚本），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 剩余库存={}",
                            trainId, trainDate, seatType, startStation, endStation, count, remainingStock);
                    return false;
                } else {
                    // 参数错误或其他错误
                    logger.error("Lua脚本执行参数错误，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, status={}",
                            trainId, trainDate, seatType, startStation, endStation, count, status);
                    return false;
                }
            }
            
            logger.error("Lua脚本返回格式异常（元素不足），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, result={}",
                    trainId, trainDate, seatType, startStation, endStation, count, result);
            return false;
        } catch (Exception e) {
            logger.error("预扣库存异常（Lua脚本），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
            return false;
        }
    }

    @Override
    public void rollback(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
        String stockKey = CacheKey.formatTrainStockKey(trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = CacheKey.formatTrainLockedKey(trainId, trainDate, seatType, startStation, endStation);
        String lockKey = "lock:" + stockKey;

        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 尝试获取锁（5秒等待，10秒租期）
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                try {
                    // 优先使用Lua脚本进行原子回滚
                    String[] keys = new String[]{stockKey, lockedKey};
                    Object[] args = new Object[]{count, STOCK_EXPIRE_SECONDS};
                    
                    List<Long> result = redisUtil.executeScriptFromResource(LUA_ROLLBACK_SCRIPT, Arrays.asList(keys), args);
                    
                    if (result != null && result.size() >= 2) {
                        long status = result.get(0);
                        long remainingStock = result.get(1);
                        
                        if (status == 1) {
                            logger.info("回滚库存成功（Lua脚本），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 剩余库存={}",
                                    trainId, trainDate, seatType, startStation, endStation, count, remainingStock);
                        } else {
                            logger.warn("回滚库存失败（Lua脚本返回状态0），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                                    trainId, trainDate, seatType, startStation, endStation, count);
                            // Lua失败时使用Redisson API回滚
                            fallbackRollback(stockKey, lockedKey, count);
                        }
                    } else {
                        logger.warn("Lua脚本返回格式异常，使用Redisson API回滚，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                                trainId, trainDate, seatType, startStation, endStation, count);
                        fallbackRollback(stockKey, lockedKey, count);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                logger.warn("获取锁超时，跳过回滚，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("回滚库存时线程被中断，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
        } catch (Exception e) {
            logger.error("回滚库存异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
        }
    }

    @Override
    public void confirm(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
        String stockKey = CacheKey.formatTrainStockKey(trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = CacheKey.formatTrainLockedKey(trainId, trainDate, seatType, startStation, endStation);
        String lockKey = "lock:" + stockKey;

        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 尝试获取锁（5秒等待，10秒租期）
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                try {
                    // 优先使用Lua脚本进行原子确认
                    String[] keys = new String[]{stockKey, lockedKey};
                    Object[] args = new Object[]{count, STOCK_EXPIRE_SECONDS};
                    
                    List<Long> result = redisUtil.executeScriptFromResource(LUA_CONFIRM_SCRIPT, Arrays.asList(keys), args);
                    
                    if (result != null && result.size() >= 2) {
                        long status = result.get(0);
                        long remainingStock = result.get(1);
                        
                        if (status == 1) {
                            logger.info("确认库存成功（Lua脚本），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 剩余库存={}",
                                    trainId, trainDate, seatType, startStation, endStation, count, remainingStock);
                        } else {
                            logger.warn("确认库存失败（Lua脚本返回状态0），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                                    trainId, trainDate, seatType, startStation, endStation, count);
                            // Lua失败时使用Redisson API确认
                            fallbackConfirm(stockKey, lockedKey, count);
                        }
                    } else {
                        logger.warn("Lua脚本返回格式异常，使用Redisson API确认，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                                trainId, trainDate, seatType, startStation, endStation, count);
                        fallbackConfirm(stockKey, lockedKey, count);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                logger.warn("获取锁超时，跳过确认，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("确认库存时线程被中断，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
        } catch (Exception e) {
            logger.error("确认库存异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
        }
    }

    @Override
    public void initStock(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock, boolean force) {
        String stockKey = CacheKey.formatTrainStockKey(trainId, trainDate, seatType, startStation, endStation);
        String lockKey = "lock:init:" + stockKey;

        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                try {
                    // 检查是否已存在库存数据
                    Boolean exists = redisUtil.exists(stockKey);
                    if (!force && Boolean.TRUE.equals(exists)) {
                        logger.info("库存已存在，跳过初始化: stockKey={}", stockKey);
                        return;
                    }

                    // 初始化库存
                    redisUtil.set(stockKey, stock, STOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
                    logger.info("库存初始化成功: stockKey={}, stock={}", stockKey, stock);
                } finally {
                    lock.unlock();
                }
            } else {
                logger.warn("获取初始化锁超时，跳过库存初始化，stockKey={}", stockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("初始化库存时线程被中断，stockKey={}", stockKey, e);
        } catch (Exception e) {
            logger.error("初始化库存异常，stockKey={}", stockKey, e);
        }
    }

    /**
     * 回滚降级方案（使用Redisson API）
     */
    private void fallbackRollback(String stockKey, String lockedKey, int count) {
        try {
            RAtomicLong stockAtomic = redissonClient.getAtomicLong(stockKey);
            RAtomicLong lockedAtomic = redissonClient.getAtomicLong(lockedKey);
            
            // 原子增加库存，减少预占库存
            stockAtomic.addAndGet(count);
            lockedAtomic.addAndGet(-count);
            
            // 重新设置过期时间
            stockAtomic.expire(STOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
            lockedAtomic.expire(LOCKED_EXPIRE_SECONDS, TimeUnit.SECONDS);
            
            logger.info("回滚库存成功（Redisson降级），stockKey={}, lockedKey={}, count={}, 当前库存={}, 当前预占={}",
                    stockKey, lockedKey, count, stockAtomic.get(), lockedAtomic.get());
        } catch (Exception e) {
            logger.error("Redisson回滚降级也失败，stockKey={}, lockedKey={}, count={}", stockKey, lockedKey, count, e);
        }
    }

    /**
     * 确认降级方案（使用Redisson API）
     */
    private void fallbackConfirm(String stockKey, String lockedKey, int count) {
        try {
            RAtomicLong lockedAtomic = redissonClient.getAtomicLong(lockedKey);
            
            // 原子减少预占库存
            lockedAtomic.addAndGet(-count);
            
            // 重新设置过期时间
            lockedAtomic.expire(LOCKED_EXPIRE_SECONDS, TimeUnit.SECONDS);
            
            logger.info("确认库存成功（Redisson降级），stockKey={}, lockedKey={}, count={}, 当前预占={}",
                    stockKey, lockedKey, count, lockedAtomic.get());
        } catch (Exception e) {
            logger.error("Redisson确认降级也失败，stockKey={}, lockedKey={}, count={}", stockKey, lockedKey, count, e);
        }
    }
}