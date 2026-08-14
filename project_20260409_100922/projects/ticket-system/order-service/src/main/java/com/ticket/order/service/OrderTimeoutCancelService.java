package com.ticket.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.dto.RouteLeg;
import com.ticket.dto.TrainStockCommands;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.OrderRouteLeg;
import com.ticket.enums.BusinessStatus;
import com.ticket.order.integration.TrainOrderGateway;
import com.ticket.order.mapper.OrderItemMapper;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.order.mapper.OrderRouteLegMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 未支付订单超时取消服务。
 *
 * <p><b>解决的问题</b>：下单时 Redis 预扣库存（stock -= n, locked += n），预占 key 仅靠
 * 30min TTL 被动过期。但 TTL 过期只是删除 {@code lockedKey}，<b>不会把预占数量加回
 * {@code stockKey}</b>，导致这部分票永久"消失"（少卖）。本服务在 TTL 到期前主动回滚预占，
 * 让库存回到可售状态。</p>
 *
 * <p><b>多实例安全</b>：定时任务在每个 order-service 实例都会触发，通过
 * {@link #claimExpiredOrder} 的「UPDATE ... WHERE status=0」乐观抢占实现互斥，
 * 只有一个实例能成功置为取消态，其余实例 affected=0 直接跳过。</p>
 *
 * <p><b>阈值约束</b>：{@code pay-timeout-minutes} 必须小于 train-service 的
 * {@code LOCKED_EXPIRE_SECONDS}(30min)，否则 Redis 预占已过期删除后再回滚会误加回 stock
 * 造成超卖。</p>
 */
@Service
public class OrderTimeoutCancelService {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCancelService.class);

    /** 单次扫描分页大小 */
    private static final int SCAN_PAGE_SIZE = 100;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    private OrderRouteLegMapper orderRouteLegMapper;

    @Resource
    private TrainOrderGateway trainOrderGateway;

    /** 未支付订单存活时长（分钟），必须小于 Redis 预占 TTL(30min) */
    @Value("${order.pay-timeout-minutes:15}")
    private int payTimeoutMinutes;

    /**
     * 每 60s 扫描一次超时未支付订单并取消。
     * cron 表达式可在 Nacos 配置覆盖。
     */
    @Scheduled(cron = "${order.timeout-cancel-cron:0 * * * * ?}")
    public void scanAndCancelExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(payTimeoutMinutes);
        long lastId = 0L;
        int totalCancelled = 0;

        while (true) {
            List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .eq(Order::getStatus, BusinessStatus.ORDER_STATUS_PENDING)
                    .lt(Order::getCreatedAt, deadline)
                    .gt(Order::getId, lastId)
                    .orderByAsc(Order::getId)
                    .last("LIMIT " + SCAN_PAGE_SIZE));

            if (orders.isEmpty()) {
                break;
            }

            for (Order order : orders) {
                lastId = order.getId();
                if (tryCancelExpiredOrder(order)) {
                    totalCancelled++;
                }
            }

            if (orders.size() < SCAN_PAGE_SIZE) {
                break;
            }
        }

        if (totalCancelled > 0) {
            log.info("超时未支付订单扫描完成，本次取消 {} 笔", totalCancelled);
        }
    }

    /**
     * 乐观抢占单笔订单：先将待支付置为已取消，抢占成功再回滚库存。
     *
     * @return true 表示本实例成功取消并回滚；false 表示被其他实例抢占或异常
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean tryCancelExpiredOrder(Order order) {
        // 乐观抢占：仅当仍是待支付时才更新成功，多实例天然互斥
        int affected = orderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Order>()
                .eq("id", order.getId())
                .eq("status", BusinessStatus.ORDER_STATUS_PENDING)
                .set("status", BusinessStatus.ORDER_STATUS_CANCELLED));

        if (affected == 0) {
            return false;
        }

        try {
            releaseReservedStock(order);
            log.info("超时未支付订单已取消并释放预占: orderNo={}, orderId={}",
                    order.getOrderNo(), order.getId());
            return true;
        } catch (Exception e) {
            // 已置为取消态但回滚失败：记录告警，由库存对账任务兜底，不再恢复订单态
            log.error("超时订单释放预占失败（已标记取消，需人工/对账兜底）: orderNo={}, orderId={}, error={}",
                    order.getOrderNo(), order.getId(), e.getMessage(), e);
            return true;
        }
    }

    /**
     * 回滚该订单的 Redis 预占库存，与 {@code OrderQueueConsumer#releasePrelockedStock} 一致：
     * 未支付时库存仍在 locked 状态，调用 reservationRollback 而非 rollbackStock。
     */
    private void releaseReservedStock(Order order) {
        int pax = Math.toIntExact(orderItemMapper.selectCount(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())));
        if (pax <= 0) {
            return;
        }

        List<OrderRouteLeg> legs = orderRouteLegMapper.selectList(
                new LambdaQueryWrapper<OrderRouteLeg>()
                        .eq(OrderRouteLeg::getOrderId, order.getId())
                        .orderByAsc(OrderRouteLeg::getLegSeq));

        if (legs != null && !legs.isEmpty()) {
            List<RouteLeg> routeLegs = new ArrayList<>(legs.size());
            for (OrderRouteLeg leg : legs) {
                RouteLeg rl = new RouteLeg();
                rl.setSegmentId(leg.getSegmentTrainId());
                rl.setFromStation(leg.getFromStation());
                rl.setToStation(leg.getToStation());
                routeLegs.add(rl);
            }
            trainOrderGateway.reservationRollbackBatch(TrainStockCommands.fromRouteLegs(
                    routeLegs, order.getTrainDate().toString(), order.getSeatType(), pax));
        } else {
            // 与入队预扣 / MQ失败回滚 / 消费失败回滚保持一致，使用原始站名，禁止 normalize，
            // 否则 Redis 预占 key 不匹配导致预占永不释放（少卖）。
            trainOrderGateway.rollbackReservation(
                    order.getTrainId(), order.getTrainDate().toString(),
                    order.getStartStation(),
                    order.getEndStation(),
                    order.getSeatType(), pax);
        }
    }
}
