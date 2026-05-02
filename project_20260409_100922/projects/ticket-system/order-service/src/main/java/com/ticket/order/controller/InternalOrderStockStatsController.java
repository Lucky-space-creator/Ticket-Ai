package com.ticket.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.enums.BusinessStatus;
import com.ticket.order.mapper.OrderItemMapper;
import com.ticket.order.mapper.OrderMapper;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 微服务间调用：供 train-service 对账时统计有效订单占座数量，避免车次域直连订单表。
 */
@RestController
@RequestMapping("/api/internal/orders/stock-stats")
public class InternalOrderStockStatsController {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private OrderItemMapper orderItemMapper;

    /**
     * 统计有效订单项数量
     * @param trainId 车次ID
     * @param trainDate 车次日期
     * @param seatType 座位类型
     * @param startStation 出发站
     * @param endStation 到达站
     * @return 订单项数量
     */
    @GetMapping("/valid-item-count")
    public int countValidOrderItems(
            @RequestParam Long trainId,
            @RequestParam String trainDate,
            @RequestParam Integer seatType,
            @RequestParam String startStation,
            @RequestParam String endStation) {
        LocalDate date = LocalDate.parse(trainDate);
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getTrainId, trainId)
                .eq(Order::getTrainDate, date)
                .eq(Order::getSeatType, seatType)
                .eq(Order::getStartStation, startStation)
                .eq(Order::getEndStation, endStation)
                .in(Order::getStatus, BusinessStatus.ORDER_STATUS_PENDING, BusinessStatus.ORDER_STATUS_PAID);
        List<Order> validOrders = orderMapper.selectList(orderWrapper);
        if (validOrders.isEmpty()) {
            return 0;
        }
        List<Long> orderIds = validOrders.stream().map(Order::getId).toList();
        LambdaQueryWrapper<OrderItem> batchWrapper = new LambdaQueryWrapper<>();
        batchWrapper.in(OrderItem::getOrderId, orderIds);
        return Math.toIntExact(orderItemMapper.selectCount(batchWrapper));
    }
}
