package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.TicketStock;
import com.ticket.enums.CacheKey;
import com.ticket.mapper.OrderItemMapper;
import com.ticket.mapper.OrderMapper;
import com.ticket.mapper.TicketStockMapper;
import com.ticket.entity.ReconciliationResult;
import com.ticket.service.StockLockService;
import com.ticket.service.StockReconciliationService;
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
 * 库存对账服务实现
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
    private OrderMapper orderMapper;

    @Resource
    private OrderItemMapper orderItemMapper;

    @Override
    public ReconciliationResult reconcileAll() {
        ReconciliationResult result = new ReconciliationResult();
        long startTime = System.currentTimeMillis();

        // P1-2: 添加分布式锁，防止多实例同时对账
        RLock reconciliationLock = redissonClient.getLock("stock:reconciliation:lock");
        boolean locked = false;
        try {
            // 尝试获取锁，等待5秒，锁持有时间30秒
            locked = reconciliationLock.tryLock(5, 30, TimeUnit.SECONDS);
            if (!locked) {
                logger.warn("获取对账分布式锁失败，可能已有其他实例正在执行对账，本次跳过");
                result.setErrorSummary("获取分布式锁失败，跳过对账");
                return result;
            }

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

        } catch (InterruptedException e) {
            logger.error("对账锁获取被中断", e);
            Thread.currentThread().interrupt();
            result.setErrorSummary("对账锁获取被中断: " + e.getMessage());
        } catch (Exception e) {
            logger.error("全量库存对账异常", e);
            result.setErrorSummary("全量对账异常: " + e.getMessage());
        } finally {
            // 释放锁
            if (locked && reconciliationLock.isHeldByCurrentThread()) {
                reconciliationLock.unlock();
            }
            // 计算耗时（毫秒）
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
     * 基于订单数据计算正确库存，确保MySQL和Redis一致
     */
    private void reconcileStock(TicketStock stock, ReconciliationResult result) {
        Long trainId = stock.getTrainId();
        String trainDate = stock.getTrainDate().toString();
        Integer seatType = stock.getSeatType();
        String startStation = stock.getStartStation();
        String endStation = stock.getEndStation();
        int dbAvailableSeats = stock.getAvailableSeats();
        Integer dbTotalSeats = stock.getTotalSeats();

        // 计算有效订单数量（状态为待支付和已支付）
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getTrainId, trainId)
                .eq(Order::getTrainDate, stock.getTrainDate())
                .eq(Order::getSeatType, seatType)
                .eq(Order::getStartStation, startStation)
                .eq(Order::getEndStation, endStation)
                .in(Order::getStatus, 
                    com.ticket.enums.BusinessStatus.ORDER_STATUS_PENDING,
                    com.ticket.enums.BusinessStatus.ORDER_STATUS_PAID);
        List<Order> validOrders = orderMapper.selectList(orderWrapper);

        // 计算有效订单项数量（状态为待支付和已支付）- 批量查询优化，解决N+1问题
        int validOrderItemCount = 0;
        if (!validOrders.isEmpty()) {
            List<Long> orderIds = validOrders.stream().map(Order::getId).toList();
            LambdaQueryWrapper<OrderItem> batchWrapper = new LambdaQueryWrapper<>();
            batchWrapper.in(OrderItem::getOrderId, orderIds);
            validOrderItemCount = Math.toIntExact(orderItemMapper.selectCount(batchWrapper));
        }

        // 计算正确库存
        if (dbTotalSeats == null || dbTotalSeats <= 0) {
            // 如果总座位数未设置，则设置为当前可用座位数 + 有效订单数（即初始库存）
            dbTotalSeats = dbAvailableSeats + validOrderItemCount;
            stock.setTotalSeats(dbTotalSeats);
            logger.info("设置总座位数: trainId={}, trainDate={}, seatType={}, totalSeats={}",
                    trainId, trainDate, seatType, dbTotalSeats);
        }

        // 计算正确可用座位数
        int correctAvailableSeats = dbTotalSeats - validOrderItemCount;
        
        // 获取Redis中的库存（使用Redisson原子长整型）
        String stockKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType, startStation, endStation);
        String lockedKey = String.format(CacheKey.TRAIN_LOCKED, trainId, trainDate, seatType, startStation, endStation);
        RAtomicLong redisStockAtomic = redissonClient.getAtomicLong(stockKey);
        RAtomicLong redisLockedAtomic = redissonClient.getAtomicLong(lockedKey);
        long redisStock = redisStockAtomic.isExists() ? redisStockAtomic.get() : 0;
        long redisLocked = redisLockedAtomic.isExists() ? redisLockedAtomic.get() : 0;
        int redisLockedValue = (int) redisLocked;
        int redisTotal = (int) (redisStock + redisLocked);

        // 检查是否需要更新数据库
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

        // 检查Redis是否需要同步（以修复后的数据库库存为准）
        // Redis库存应等于目标库存减去预占库存（因为预占库存已从可用库存中扣除）
        // 但Redis中的stockKey是可用库存，lockedKey是预占库存，所以stockKey应等于目标库存减去预占库存
        int targetRedisStockKeyValue = correctAvailableSeats - redisLockedValue;
        if (targetRedisStockKeyValue < 0) {
            // 预占库存超过目标库存，说明有异常，重置预占库存为0
            logger.warn("预占库存超过目标库存，重置预占: trainId={}, trainDate={}, seatType={}, locked={}, target={}",
                    trainId, trainDate, seatType, redisLockedValue, correctAvailableSeats);
            redisLockedAtomic.set(0);
            targetRedisStockKeyValue = correctAvailableSeats;
        }
        
        boolean redisStockExists = redisStockAtomic.isExists();
        if (!redisStockExists || redisStock != targetRedisStockKeyValue) {
            logger.warn("Redis库存不一致，需要修复: trainId={}, trainDate={}, seatType={}, redisCurrent={}, redisTarget={}, locked={}",
                    trainId, trainDate, seatType, redisStock, targetRedisStockKeyValue, redisLockedValue);
            // P0修复：使用强制覆盖初始化Redis库存，保留合法预占库存
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