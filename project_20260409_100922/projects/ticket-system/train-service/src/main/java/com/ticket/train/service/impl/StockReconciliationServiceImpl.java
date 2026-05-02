package com.ticket.train.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.ReconciliationResult;
import com.ticket.entity.TicketStock;
import com.ticket.enums.CacheKey;
import com.ticket.service.StockLockService;
import com.ticket.train.client.OrderStockStatsClient;
import com.ticket.train.mapper.TicketStockMapper;
import com.ticket.train.service.StockReconciliationService;
import jakarta.annotation.Resource;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 库存对账：MySQL 余票与 Redis 对齐；有效占座数通过 order-service 内部接口查询，保持车次域与订单域解耦。
 */
@Service
public class StockReconciliationServiceImpl implements StockReconciliationService {

    private static final Logger logger = LoggerFactory.getLogger(StockReconciliationServiceImpl.class);

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StockLockService stockLockService;

    @Resource
    private OrderStockStatsClient orderStockStatsClient;

    @Override
    public ReconciliationResult reconcileAll() {
        ReconciliationResult result = new ReconciliationResult();
        long startTime = System.currentTimeMillis();

        RLock reconciliationLock = redissonClient.getLock("stock:reconciliation:lock");
        boolean locked = false;
        try {
            locked = reconciliationLock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) {
                logger.warn("获取对账分布式锁失败，可能已有其他实例正在执行对账，本次跳过");
                result.setErrorSummary("获取分布式锁失败，跳过对账");
                return result;
            }

            List<TicketStock> stocks = ticketStockMapper.selectList(new LambdaQueryWrapper<>());
            logger.info("开始全量库存对账，共 {} 条记录", stocks.size());

            for (TicketStock stock : stocks) {
                try {
                    reconcileStock(stock, result);
                } catch (Exception e) {
                    logger.error("对账记录异常: trainId={}, trainDate={}, seatType={}",
                            stock.getTrainId(), stock.getTrainDate(), stock.getSeatType(), e);
                    result.incrementError();
                }
            }

            logger.info("全量库存对账完成，成功: {}，不一致: {}，修复: {}，错误: {}",
                    result.getSuccessCount(), result.getMismatchCount(),
                    result.getFixedCount(), result.getErrorCount());

        } catch (InterruptedException e) {
            logger.error("对账锁获取被中断", e);
            Thread.currentThread().interrupt();
            result.setErrorSummary("对账锁获取被中断: " + e.getMessage());
        } catch (Exception e) {
            logger.error("全量库存对账异常", e);
            result.setErrorSummary("全量对账异常: " + e.getMessage());
        } finally {
            if (locked && reconciliationLock.isHeldByCurrentThread()) {
                reconciliationLock.unlock();
            }
            result.setDurationMs(System.currentTimeMillis() - startTime);
        }

        return result;
    }

    @Override
    public ReconciliationResult reconcileStock(Long trainId, String trainDate, Integer seatType) {
        ReconciliationResult result = new ReconciliationResult();
        long startTime = System.currentTimeMillis();

        try {
            LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TicketStock::getTrainId, trainId)
                    .eq(TicketStock::getTrainDate, java.time.LocalDate.parse(trainDate))
                    .eq(TicketStock::getSeatType, seatType);

            TicketStock stock = ticketStockMapper.selectOne(wrapper);
            if (stock == null) {
                logger.warn("对账记录不存在: trainId={}, trainDate={}, seatType={}",
                        trainId, trainDate, seatType);
                result.setErrorSummary("记录不存在");
                return result;
            }

            reconcileStock(stock, result);

        } catch (Exception e) {
            logger.error("单条库存对账异常: trainId={}, trainDate={}, seatType={}",
                    trainId, trainDate, seatType, e);
            result.setErrorSummary("对账异常: " + e.getMessage());
        } finally {
            result.setDurationMs(System.currentTimeMillis() - startTime);
        }

        return result;
    }

    private void reconcileStock(TicketStock stock, ReconciliationResult result) {
        Long trainId = stock.getTrainId();
        String trainDate = stock.getTrainDate() == null ? null : stock.getTrainDate().toString();
        Integer seatType = stock.getSeatType();
        String startStation = stock.getStartStation();
        String endStation = stock.getEndStation();
        int dbAvailableSeats = stock.getAvailableSeats();
        Integer dbTotalSeats = stock.getTotalSeats();

        int validOrderItemCount = orderStockStatsClient.countValidOrderItems(
                trainId, stock.getTrainDate().toString(), seatType, startStation, endStation);

        if (dbTotalSeats == null || dbTotalSeats <= 0) {
            dbTotalSeats = dbAvailableSeats + validOrderItemCount;
            stock.setTotalSeats(dbTotalSeats);
            logger.info("设置总座位数: trainId={}, trainDate={}, seatType={}, totalSeats={}",
                    trainId, trainDate, seatType, dbTotalSeats);
        }

        int correctAvailableSeats = dbTotalSeats - validOrderItemCount;

        String stockKey = CacheKey.formatTrainStockKey(trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = CacheKey.formatTrainLockedKey(trainId, trainDate, seatType, startStation, endStation);
        RAtomicLong redisStockAtomic = redissonClient.getAtomicLong(stockKey);
        RAtomicLong redisLockedAtomic = redissonClient.getAtomicLong(lockedKey);
        long redisStock = redisStockAtomic.isExists() ? redisStockAtomic.get() : 0;
        long redisLocked = redisLockedAtomic.isExists() ? redisLockedAtomic.get() : 0;
        int redisLockedValue = (int) redisLocked;

        boolean needUpdateDb = false;
        if (dbAvailableSeats != correctAvailableSeats) {
            logger.warn("数据库库存不一致，需要修复: trainId={}, trainDate={}, seatType={}, dbCurrent={}, dbCorrect={}, totalSeats={}, validOrders={}",
                    trainId, trainDate, seatType, dbAvailableSeats, correctAvailableSeats, dbTotalSeats, validOrderItemCount);
            stock.setAvailableSeats(correctAvailableSeats);
            int updateResult = ticketStockMapper.updateById(stock);
            if (updateResult > 0) {
                logger.info("数据库库存修复成功: trainId={}, trainDate={}, seatType={}, newAvailable={}",
                        trainId, trainDate, seatType, correctAvailableSeats);
                needUpdateDb = true;
                result.incrementFixed();
            } else {
                logger.error("数据库库存修复失败: trainId={}, trainDate={}, seatType={}", trainId, trainDate, seatType);
                result.incrementError();
                return;
            }
        } else {
            logger.debug("数据库库存正确: trainId={}, trainDate={}, seatType={}, available={}, total={}, validOrders={}",
                    trainId, trainDate, seatType, dbAvailableSeats, dbTotalSeats, validOrderItemCount);
        }

        int targetRedisStockKeyValue = correctAvailableSeats - redisLockedValue;
        if (targetRedisStockKeyValue < 0) {
            logger.warn("预占库存超过目标库存，重置预占: trainId={}, trainDate={}, seatType={}, locked={}, target={}",
                    trainId, trainDate, seatType, redisLockedValue, correctAvailableSeats);
            redisLockedAtomic.set(0);
            targetRedisStockKeyValue = correctAvailableSeats;
        }

        boolean redisStockExists = redisStockAtomic.isExists();
        if (!redisStockExists || redisStock != targetRedisStockKeyValue) {
            logger.warn("Redis库存不一致，需要修复: trainId={}, trainDate={}, seatType={}, redisCurrent={}, redisTarget={}, locked={}",
                    trainId, trainDate, seatType, redisStock, targetRedisStockKeyValue, redisLockedValue);
            stockLockService.initStock(trainId, trainDate, seatType, startStation, endStation, targetRedisStockKeyValue, true);
            logger.info("Redis库存修复完成，trainId={}, trainDate={}, seatType={}, newStock={}, locked={}",
                    trainId, trainDate, seatType, targetRedisStockKeyValue, redisLockedValue);
            result.incrementMismatch();
            result.incrementFixed();
        } else {
            logger.debug("Redis库存一致: trainId={}, trainDate={}, seatType={}, redis={}, locked={}",
                    trainId, trainDate, seatType, redisStock, redisLockedValue);
        }

        if (!needUpdateDb && redisStockExists && redisStock == targetRedisStockKeyValue) {
            result.incrementSuccess();
        }
    }
}
