package com.ticket.service.impl;

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
        String stockKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = String.format(CacheKey.TRAIN_LOCKED, trainId, trainDate, seatType, startStation, endStation);
        
        try {
            // 准备Lua脚本参数
            String[] keys = new String[]{stockKey, lockedKey};
            Object[] args = new Object[]{count, LOCKED_EXPIRE_SECONDS, STOCK_EXPIRE_SECONDS};
            
            // 执行Lua脚本（原子操作，一次网络往返）
            Object result = redisUtil.executeScriptFromResource(LUA_DEDUCT_SCRIPT, Arrays.asList(keys), args);
            
            if (result instanceof List) {
                List<?> resultList = (List<?>) result;
                if (resultList.size() >= 2) {
                    long status = ((Number) resultList.get(0)).longValue();
                    long remainingStock = ((Number) resultList.get(1)).longValue();
                    
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
            }
            
            logger.error("Lua脚本返回格式异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count);
            return false;
        } catch (Exception e) {
            logger.error("预扣库存异常（Lua脚本），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
            return false;
        }
    }

    @Override
    public void rollback(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
        String stockKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = String.format(CacheKey.TRAIN_LOCKED, trainId, trainDate, seatType, startStation, endStation);
        String lockKey = "lock:" + stockKey;

        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                logger.warn("获取分布式锁失败（回滚），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
                return;
            }

            RAtomicLong stockAtomic = redissonClient.getAtomicLong(stockKey);
            RAtomicLong lockedAtomic = redissonClient.getAtomicLong(lockedKey);

            long currentLocked = lockedAtomic.get();
            if (currentLocked < count) {
                logger.warn("回滚库存异常：回滚量超过预占量，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 当前预占={}",
                        trainId, trainDate, seatType, startStation, endStation, count, currentLocked);
                // 仍然执行回滚，将预占库存清零
                lockedAtomic.set(0);
            } else {
                lockedAtomic.addAndGet(-count);
            }

            // 恢复库存
            long newStock = stockAtomic.addAndGet(count);

            // 刷新过期时间
            stockAtomic.expire(STOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
            lockedAtomic.expire(LOCKED_EXPIRE_SECONDS, TimeUnit.SECONDS);

            logger.info("回滚库存成功，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 新库存={}, 剩余预占={}",
                    trainId, trainDate, seatType, startStation, endStation, count, newStock, lockedAtomic.get());
        } catch (InterruptedException e) {
            logger.error("获取锁时被中断（回滚），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("回滚库存异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void confirm(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
        String lockedKey = String.format(CacheKey.TRAIN_LOCKED, trainId, trainDate, seatType, startStation, endStation);
        String lockKey = "lock:" + lockedKey;

        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                logger.warn("获取分布式锁失败（确认），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
                return;
            }

            RAtomicLong lockedAtomic = redissonClient.getAtomicLong(lockedKey);
            long currentLocked = lockedAtomic.get();
            if (currentLocked < count) {
                logger.warn("确认扣减异常：确认量超过预占量，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 当前预占={}",
                        trainId, trainDate, seatType, startStation, endStation, count, currentLocked);
                // 仍然执行确认，将预占库存清零
                lockedAtomic.set(0);
            } else {
                lockedAtomic.addAndGet(-count);
            }

            // 刷新过期时间（确保预占库存不会过早过期）
            lockedAtomic.expire(LOCKED_EXPIRE_SECONDS, TimeUnit.SECONDS);

            logger.info("确认扣减成功，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 剩余预占={}",
                    trainId, trainDate, seatType, startStation, endStation, count, lockedAtomic.get());
        } catch (InterruptedException e) {
            logger.error("获取锁时被中断（确认），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("确认扣减异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void initStock(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock, boolean force) {
        String stockKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = String.format(CacheKey.TRAIN_LOCKED, trainId, trainDate, seatType, startStation, endStation);
        String lockKey = "lock:" + stockKey;

        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                logger.warn("获取分布式锁失败（初始化库存），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, stock={}, force={}",
                        trainId, trainDate, seatType, startStation, endStation, stock, force);
                return;
            }

            RAtomicLong stockAtomic = redissonClient.getAtomicLong(stockKey);
            RAtomicLong lockedAtomic = redissonClient.getAtomicLong(lockedKey);

            // 处理库存
            if (force || !stockAtomic.isExists()) {
                stockAtomic.set(stock);
                stockAtomic.expire(STOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
                logger.info("初始化库存成功，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, stock={}, force={}",
                        trainId, trainDate, seatType, startStation, endStation, stock, force);
            } else {
                long current = stockAtomic.get();
                logger.info("库存Key已存在，跳过覆盖，当前值={}, trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, force={}",
                        current, trainId, trainDate, seatType, startStation, endStation, force);
            }

            // 处理预占库存：force为true时保留原有预占库存（由调用方保证合法性），否则重置为0
            if (force) {
                // 强制覆盖场景下，保留原有预占库存，只刷新过期时间
                if (lockedAtomic.isExists()) {
                    logger.debug("强制初始化库存，保留预占库存，当前值={}, trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}",
                            lockedAtomic.get(), trainId, trainDate, seatType, startStation, endStation);
                }
                lockedAtomic.expire(LOCKED_EXPIRE_SECONDS, TimeUnit.SECONDS);
            } else {
                // 非强制初始化，确保预占库存为0（如果存在则重置）
                if (lockedAtomic.isExists()) {
                    lockedAtomic.set(0);
                }
                lockedAtomic.expire(LOCKED_EXPIRE_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            logger.error("获取锁时被中断（初始化库存），trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, stock={}, force={}",
                    trainId, trainDate, seatType, startStation, endStation, stock, force, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("初始化库存异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, stock={}, force={}",
                    trainId, trainDate, seatType, startStation, endStation, stock, force, e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}