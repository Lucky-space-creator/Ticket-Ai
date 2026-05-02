package com.ticket.order.service;

import com.ticket.dto.mq.OrderQueueRequest;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.Train;
import com.ticket.order.integration.TrainOrderGateway;
import com.ticket.order.mapper.OrderItemMapper;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.util.SnowflakeIdUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private TrainOrderGateway trainOrderGateway;

    @Transactional(rollbackFor = Exception.class)
    public Order createOrderFromQueue(OrderQueueRequest request) {
        BigDecimal totalAmount = request.getItems().stream()
                .map(OrderQueueRequest.PassengerItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Train train = trainOrderGateway.getTrainById(request.getTrainId());
        if (train == null) {
            throw new RuntimeException("车次信息不存在");
        }

        LocalDate trainDate = LocalDate.parse(request.getTrainDate());

        Order order = new Order();
        order.setOrderNo(SnowflakeIdUtil.getInstance().nextIdStr());
        order.setUserId(request.getUserId());
        order.setTrainId(request.getTrainId());
        order.setTrainNo(train.getTrainNo());
        order.setTrainDate(trainDate);
        order.setStartStation(request.getStartStation());
        order.setEndStation(request.getEndStation());
        order.setSeatType(request.getSeatType());
        order.setTotalAmount(totalAmount);
        order.setStatus(0);
        order.setDepartTime(LocalDateTime.of(trainDate, train.getStartTime()));

        orderMapper.insert(order);

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
}
