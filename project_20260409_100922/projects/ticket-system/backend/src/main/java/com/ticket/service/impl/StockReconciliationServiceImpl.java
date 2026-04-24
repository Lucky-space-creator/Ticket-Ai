package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.TicketStock;
import com.ticket.enums.CacheKey;
import com.ticket.mapper.TicketStockMapper;
import com.ticket.service.ReconciliationResult;
import com.ticket.service.StockLockService;
import com.ticket.service.StockReconciliationService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存对账服务实现
 */
@Service
public class StockReconciliationServiceImpl implements StockReconciliationService {

    private static final Logger logger = LoggerFactory.getLogger(StockReconciliationServiceImpl.class);

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private StockLockService stockLockService;

    @Override
    public ReconciliationResult reconcileAll() {
        ReconciliationResult result = new ReconciliationResult();
        long startTime = System.currentTimeMillis();

        try {
            // 查询所有库存记录
            LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(TicketStock::getTrainId, TicketStock::getTrainDate,
                    TicketStock::getSeatType, TicketStock::getAvailableSeats);
            List<TicketStock> stocks = ticketStockMapper.selectList(wrapper);

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

        } catch (Exception e) {
            logger.error("全量库存对账异常", e);
            result.setErrorSummary("全量对账异常: " + e.getMessage());
        } finally {
            result.setDurationMs(System.currentTimeMillis() - startTime);
        }

        return result;
    }

    @Override
    public ReconciliationResult reconcileStock(Long trainId, String trainDate, Integer seatType) {
        ReconciliationResult result = new ReconciliationResult();
        long startTime = System.currentTimeMillis();

        try {
            // 查询数据库库存
            LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TicketStock::getTrainId, trainId)
                    .eq(TicketStock::getTrainDate, trainDate)
                    .eq(TicketStock::getSeatType, seatType)
                    .select(TicketStock::getAvailableSeats);

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

    /**
     * 对账单条库存记录
     */
    private void reconcileStock(TicketStock stock, ReconciliationResult result) {
        Long trainId = stock.getTrainId();
        String trainDate = stock.getTrainDate().toString();
        Integer seatType = stock.getSeatType();
        int dbStock = stock.getAvailableSeats();

        // 获取Redis中的库存
        String stockKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType, stock.getStartStation(), stock.getEndStation());
        String lockedKey = String.format(CacheKey.TRAIN_LOCKED, trainId, trainDate, seatType, stock.getStartStation(), stock.getEndStation());

        Integer redisStock = redisUtil.get(stockKey);
        Integer redisLocked = redisUtil.get(lockedKey);

        // 如果Redis中不存在库存Key，说明可能未初始化或已过期
        if (redisStock == null) {
            // 重新初始化Redis库存
            stockLockService.initStock(trainId, trainDate, seatType, stock.getStartStation(), stock.getEndStation(), dbStock);
            logger.info("Redis库存不存在，重新初始化: trainId={}, trainDate={}, seatType={}, stock={}",
                    trainId, trainDate, seatType, dbStock);
            result.incrementFixed();
            result.incrementSuccess();
            return;
        }

        // 计算Redis中的总库存（可用 + 预占）
        int redisLockedValue = redisLocked != null ? redisLocked : 0;
        int redisTotal = redisStock + redisLockedValue;

        // 理论上，Redis总库存应该等于数据库库存（因为Redis预扣后，数据库也会扣减）
        // 但由于网络延迟、失败回滚等原因，可能存在差异
        if (redisTotal == dbStock) {
            // 一致
            logger.debug("库存一致: trainId={}, trainDate={}, seatType={}, db={}, redis={}, locked={}",
                    trainId, trainDate, seatType, dbStock, redisStock, redisLockedValue);
            result.incrementSuccess();
        } else {
            // 不一致，以数据库为准，修复Redis
            logger.warn("库存不一致，以数据库为准: trainId={}, trainDate={}, seatType={}, db={}, redis={}, locked={}",
                    trainId, trainDate, seatType, dbStock, redisStock, redisLockedValue);

            // 重新初始化Redis库存（覆盖现有值）
            stockLockService.initStock(trainId, trainDate, seatType, stock.getStartStation(), stock.getEndStation(), dbStock);
            // 清空预占库存（因为数据库没有预占概念，所有库存都应该是可用的）
            redisUtil.delete(lockedKey);

            result.incrementMismatch();
            result.incrementFixed();
        }
    }
}