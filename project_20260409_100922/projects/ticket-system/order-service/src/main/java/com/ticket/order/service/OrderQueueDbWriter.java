package com.ticket.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.dto.RouteLeg;
import com.ticket.dto.mq.OrderQueueRequest;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.OrderRouteLeg;
import com.ticket.entity.Train;
import com.ticket.enums.RouteType;
import com.ticket.order.integration.TrainOrderGateway;
import com.ticket.order.mapper.OrderItemMapper;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.order.mapper.OrderRouteLegMapper;
import com.ticket.util.SnowflakeIdUtil;
import jakarta.annotation.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 将 MQ 排队结果写入订单库；独立成类以保证 {@link Transactional} 代理生效。
 */
@Service
public class OrderQueueDbWriter {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    private OrderRouteLegMapper orderRouteLegMapper;

    @Resource
    private TrainOrderGateway trainOrderGateway;

    @Transactional(rollbackFor = Exception.class)
    public Order createOrderFromQueue(OrderQueueRequest request) {
        BigDecimal totalAmount = request.getItems().stream()
                .map(OrderQueueRequest.PassengerItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RouteLeg> legs = request.getLegs();
        Train trainFallback = legs == null || legs.isEmpty()
                ? trainOrderGateway.getTrainById(request.getTrainId())
                : trainOrderGateway.getTrainById(legs.get(0).getSegmentId());
        if (trainFallback == null) {
            throw new RuntimeException("车次信息不存在");
        }

        LocalDate trainDate = LocalDate.parse(request.getTrainDate());

        RouteLeg firstLeg = legs != null && !legs.isEmpty() ? legs.get(0) : null;

        Order order = new Order();
        order.setOrderNo(SnowflakeIdUtil.getInstance().nextIdStr());
        order.setUserId(request.getUserId());
        order.setQueueRequestId(request.getRequestId());
        order.setTrainId(firstLeg != null ? firstLeg.getSegmentId() : request.getTrainId());
        order.setTrainNo(firstLeg != null ? firstLeg.getTrainNo() : trainFallback.getTrainNo());
        order.setRouteSku(request.getRouteSku());
        if (request.getRouteType() != null && !request.getRouteType().isBlank()) {
            order.setRouteType(request.getRouteType());
        } else {
            order.setRouteType(inferRouteType(legs));
        }
        order.setTrainDate(trainDate);
        order.setStartStation(request.getStartStation());
        order.setEndStation(request.getEndStation());
        order.setSeatType(request.getSeatType());
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        if (firstLeg != null) {
            Train seg0 = trainOrderGateway.getTrainById(firstLeg.getSegmentId());
            order.setDepartTime(seg0 != null
                    ? LocalDateTime.of(trainDate, seg0.getStartTime())
                    : LocalDateTime.of(trainDate, trainFallback.getStartTime()));
        } else {
            order.setDepartTime(LocalDateTime.of(trainDate, trainFallback.getStartTime()));
        }

        try {
            orderMapper.insert(order);
        } catch (DataIntegrityViolationException ex) {
            if (request.getRequestId() == null) {
                throw ex;
            }
            Order existing = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                    .eq(Order::getQueueRequestId, request.getRequestId())
                    .last("LIMIT 1"));
            if (existing == null) {
                throw ex;
            }
            return existing;
        }

        if (legs != null && !legs.isEmpty()) {
            int seq = 1;
            for (RouteLeg rl : legs) {
                Train seg = trainOrderGateway.getTrainById(rl.getSegmentId());
                if (seg == null) {
                    throw new RuntimeException("行程线段不存在: " + rl.getSegmentId());
                }
                OrderRouteLeg orl = new OrderRouteLeg();
                orl.setOrderId(order.getId());
                orl.setLegSeq(seq++);
                orl.setSegmentTrainId(rl.getSegmentId());
                orl.setTrainNo(rl.getTrainNo());
                orl.setFromStation(rl.getFromStation());
                orl.setToStation(rl.getToStation());
                orl.setSegmentPrice(rl.getSegmentPrice() != null ? rl.getSegmentPrice() : BigDecimal.ZERO);
                orl.setPlannedDepartAt(LocalDateTime.of(trainDate, seg.getStartTime()));
                orl.setPlannedArriveAt(LocalDateTime.of(trainDate, seg.getEndTime()));
                orderRouteLegMapper.insert(orl);
            }
        } else {
            OrderRouteLeg orl = new OrderRouteLeg();
            orl.setOrderId(order.getId());
            orl.setLegSeq(1);
            orl.setSegmentTrainId(request.getTrainId());
            orl.setTrainNo(trainFallback.getTrainNo());
            orl.setFromStation(request.getStartStation());
            orl.setToStation(request.getEndStation());
            BigDecimal pp = request.getItems().get(0).getPrice();
            orl.setSegmentPrice(pp != null ? pp : BigDecimal.ZERO);
            orl.setPlannedDepartAt(order.getDepartTime());
            orl.setPlannedArriveAt(LocalDateTime.of(trainDate, trainFallback.getEndTime()));
            orderRouteLegMapper.insert(orl);
        }

        for (OrderQueueRequest.PassengerItem item : request.getItems()) {
            OrderItem detail = new OrderItem();
            detail.setOrderId(order.getId());
            detail.setPassengerName(item.getPassengerName());
            detail.setIdCard(item.getIdCard());
            detail.setPrice(item.getPrice());
            orderItemMapper.insert(detail);
        }

        return order;
    }

    private static String inferRouteType(List<RouteLeg> legs) {
        if (legs == null || legs.isEmpty()) {
            return RouteType.SINGLE;
        }
        if (legs.size() == 1) {
            return RouteType.SINGLE;
        }
        String tn0 = legs.get(0).getTrainNo();
        for (RouteLeg l : legs) {
            if (l.getTrainNo() == null || !l.getTrainNo().equals(tn0)) {
                return RouteType.TRANSFER;
            }
        }
        return RouteType.DIRECT;
    }
}
