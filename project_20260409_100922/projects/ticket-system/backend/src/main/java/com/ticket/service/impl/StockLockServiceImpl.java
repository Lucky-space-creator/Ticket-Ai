package com.ticket.service.impl;

import com.ticket.enums.CacheKey;
import com.ticket.service.StockLockService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 库存锁服务实现
 */
@Service
public class StockLockServiceImpl implements StockLockService {

    private static final Logger logger = LoggerFactory.getLogger(StockLockServiceImpl.class);

    @Resource
    private RedisUtil redisUtil;

    // 预占库存过期时间（30分钟）
    private static final int LOCKED_EXPIRE_SECONDS = 30 * 60;
    // 库存缓存过期时间（1天）
    private static final int STOCK_EXPIRE_SECONDS = 24 * 60 * 60;

    @Override
    public boolean tryDeduct(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
        String stockKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = String.format(CacheKey.TRAIN_LOCKED, trainId, trainDate, seatType, startStation, endStation);

        try {
            // 执行Lua脚本
            List<String> keys = Arrays.asList(stockKey, lockedKey);
            Object result = redisUtil.executeScriptFromResource("lua/deduct_stock.lua", keys,
                    new Object[]{Integer.valueOf(count), Integer.valueOf(LOCKED_EXPIRE_SECONDS), Integer.valueOf(STOCK_EXPIRE_SECONDS)});

            if (result == null) {
                logger.error("Lua脚本执行返回null，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
                return false;
            }

            // 解析结果：{状态码, 剩余库存}
            List<?> resultList = (List<?>) result;
            if (resultList.size() < 2) {
                logger.error("Lua脚本返回结果格式错误，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
                return false;
            }
            
            long status = ((Number) resultList.get(0)).longValue();
            long remaining = ((Number) resultList.get(1)).longValue();

            if (status == 1) {
                logger.info("预扣库存成功，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 剩余库存={}",
                        trainId, trainDate, seatType, startStation, endStation, count, remaining);
                return true;
            } else if (status == 0) {
                logger.warn("库存不足，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 剩余库存={}",
                        trainId, trainDate, seatType, startStation, endStation, count, remaining);
                return false;
            } else {
                logger.error("Lua脚本执行异常，状态码={}, trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        status, trainId, trainDate, seatType, startStation, endStation, count);
                return false;
            }
        } catch (Exception e) {
            logger.error("预扣库存异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}", 
                    trainId, trainDate, seatType, startStation, endStation, count, e);
            return false;
        }
    }

    @Override
    public void rollback(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
        String stockKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = String.format(CacheKey.TRAIN_LOCKED, trainId, trainDate, seatType, startStation, endStation);

        try {
            List<String> keys = Arrays.asList(stockKey, lockedKey);
            Object result = redisUtil.executeScriptFromResource("lua/rollback_stock.lua", keys,
                    new Object[]{Integer.valueOf(count), Integer.valueOf(STOCK_EXPIRE_SECONDS)});

            if (result == null) {
                logger.error("回滚库存Lua脚本返回null，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
                return;
            }

            List<?> resultList = (List<?>) result;
            if (resultList.size() < 2) {
                logger.error("回滚库存Lua脚本返回结果格式错误，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
                return;
            }
            
            long status = ((Number) resultList.get(0)).longValue();
            long newLocked = ((Number) resultList.get(1)).longValue();

            if (status == 1) {
                logger.info("回滚库存成功，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 剩余预占={}",
                        trainId, trainDate, seatType, startStation, endStation, count, newLocked);
            } else if (status == -1) {
                logger.warn("回滚库存异常：回滚量超过预占量，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
            } else {
                logger.error("回滚库存Lua脚本异常，状态码={}, trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        status, trainId, trainDate, seatType, startStation, endStation, count);
            }
        } catch (Exception e) {
            logger.error("回滚库存异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
        }
    }

    @Override
    public void confirm(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
        String lockedKey = String.format(CacheKey.TRAIN_LOCKED, trainId, trainDate, seatType, startStation, endStation);

        try {
            List<String> keys = Arrays.asList(lockedKey);
            Object result = redisUtil.executeScriptFromResource("lua/confirm_stock.lua", keys,
                    new Object[]{Integer.valueOf(count)});

            if (result == null) {
                logger.error("确认扣减Lua脚本返回null，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
                return;
            }

            List<?> resultList = (List<?>) result;
            if (resultList.size() < 2) {
                logger.error("确认扣减Lua脚本返回结果格式错误，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
                return;
            }
            
            long status = ((Number) resultList.get(0)).longValue();
            long newLocked = ((Number) resultList.get(1)).longValue();

            if (status == 1) {
                logger.info("确认扣减成功，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}, 剩余预占={}",
                        trainId, trainDate, seatType, startStation, endStation, count, newLocked);
            } else if (status == -1) {
                logger.warn("确认扣减异常：确认量超过预占量，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        trainId, trainDate, seatType, startStation, endStation, count);
            } else {
                logger.error("确认扣减Lua脚本异常，状态码={}, trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                        status, trainId, trainDate, seatType, startStation, endStation, count);
            }
        } catch (Exception e) {
            logger.error("确认扣减异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, count={}",
                    trainId, trainDate, seatType, startStation, endStation, count, e);
        }
    }

    @Override
    public void initStock(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock) {
        String stockKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType, startStation, endStation);

        try {
            List<String> keys = Arrays.asList(stockKey);
            Object result = redisUtil.executeScriptFromResource("lua/init_stock.lua", keys,
                    new Object[]{Integer.valueOf(stock), Integer.valueOf(STOCK_EXPIRE_SECONDS)});

            if (result == null) {
                logger.error("初始化库存Lua脚本返回null，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, stock={}",
                        trainId, trainDate, seatType, startStation, endStation, stock);
                return;
            }

            List<?> resultList = (List<?>) result;
            if (resultList.size() < 2) {
                logger.error("初始化库存Lua脚本返回结果格式错误，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, stock={}",
                        trainId, trainDate, seatType, startStation, endStation, stock);
                return;
            }
            
            long status = ((Number) resultList.get(0)).longValue();
            long value = ((Number) resultList.get(1)).longValue();

            if (status == 1) {
                logger.info("初始化库存成功，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, stock={}",
                        trainId, trainDate, seatType, startStation, endStation, stock);
            } else if (status == -1) {
                logger.info("库存Key已存在，当前值={}, trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}",
                        value, trainId, trainDate, seatType, startStation, endStation);
            } else {
                logger.error("初始化库存Lua脚本异常，状态码={}, trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, stock={}",
                        status, trainId, trainDate, seatType, startStation, endStation, stock);
            }
        } catch (Exception e) {
            logger.error("初始化库存异常，trainId={}, trainDate={}, seatType={}, startStation={}, endStation={}, stock={}",
                    trainId, trainDate, seatType, startStation, endStation, stock, e);
        }
    }
}