package com.ticket.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.dto.mq.OrderCreatedEvent;
import com.ticket.dto.mq.PaymentConfirmedEvent;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.Train;
import com.ticket.entity.User;
import com.ticket.enums.BusinessStatus;
import com.ticket.enums.CacheKey;
import com.ticket.enums.ResponseCode;
import com.ticket.order.mapper.OrderItemMapper;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.order.service.OrderService;
import com.ticket.service.RocketMQProducerService;
import com.ticket.train.service.StockLockService;
import com.ticket.train.service.TrainService;
import com.ticket.user.service.UserService;
import com.ticket.util.MQIdempotentUtil;
import com.ticket.util.RedisUtil;
import com.ticket.util.SnowflakeIdUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务实现
 * 核心路径（Redis预扣+DB写订单）保持同步事务，非关键操作异步化到RocketMQ
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    private TrainService trainService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private UserService userService;

    @Resource
    private StockLockService stockLockService;

    @Resource
    private RocketMQProducerService rocketMQProducerService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, Long trainId, String trainDate,
                             String startStation, String endStation,
                             Integer seatType, List<OrderItem> items) {
        // 1. 检查车次信息
        Train train = trainService.getById(trainId);
        if (train == null || Objects.equals(train.getStatus(), BusinessStatus.TRAIN_STATUS_STOPPED)) {
            throw new RuntimeException(ResponseCode.TRAIN_NOT_FOUND.getMessage());
        }

        // 2. 扣减库存（仅Redis预扣）- 核心同步路径
        trainService.deductStock(trainId, trainDate, startStation, endStation, seatType, items.size());

        try {
            // 3. 计算总价
            BigDecimal totalAmount = items.stream()
                    .map(OrderItem::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 4. 创建订单
            Order order = new Order();
            order.setOrderNo(SnowflakeIdUtil.getInstance().nextIdStr());
            order.setUserId(userId);
            order.setTrainId(trainId);
            order.setTrainNo(train.getTrainNo());
            order.setTrainDate(LocalDate.parse(trainDate));
            order.setStartStation(startStation);
            order.setEndStation(endStation);
            order.setDepartTime(LocalDateTime.of(LocalDate.parse(trainDate), train.getStartTime()));
            order.setSeatType(seatType);
            order.setTotalAmount(totalAmount);
            order.setStatus(BusinessStatus.ORDER_STATUS_PENDING);

            save(order);

            // 5. 保存订单明细
            for (OrderItem item : items) {
                item.setOrderId(order.getId());
                orderItemMapper.insert(item);
            }

            // 6. 异步：发送订单创建事件（缓存清理等由消费者处理）
            OrderCreatedEvent event = new OrderCreatedEvent();
            event.setMessageId(idempotentUtil.generateMessageId());
            event.setOrderId(order.getId());
            event.setOrderNo(order.getOrderNo());
            event.setUserId(userId);
            event.setTrainId(trainId);
            event.setTrainNo(train.getTrainNo());
            event.setTrainDate(LocalDate.parse(trainDate));
            event.setStartStation(startStation);
            event.setEndStation(endStation);
            event.setSeatType(seatType);
            event.setTotalAmount(totalAmount);
            event.setItemCount(items.size());
            event.setCreatedAt(LocalDateTime.now());
            rocketMQProducerService.sendOrderCreatedEvent(event);

            return order;
        } catch (Exception e) {
            // 订单创建失败，回滚Redis预占库存
            logger.error("创建订单失败，回滚库存: userId={}, trainId={}, trainDate={}, seatType={}, count={}",
                    userId, trainId, trainDate, seatType, items.size(), e);
            stockLockService.rollback(trainId, trainDate, seatType, startStation, endStation, items.size());
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payOrder(Long userId, String orderNo) {
        // 查询订单
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId);

        Order order = getOne(wrapper);
        if (order == null) {
            throw new RuntimeException(ResponseCode.ORDER_NOT_FOUND.getMessage());
        }

        // 检查状态
        if (!Objects.equals(order.getStatus(), BusinessStatus.ORDER_STATUS_PENDING)) {
            throw new RuntimeException(ResponseCode.ORDER_PAID.getMessage());
        }

        // 查询订单明细数量
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, order.getId());
        int count = Math.toIntExact(orderItemMapper.selectCount(itemWrapper));

        // 更新状态为已支付
        order.setStatus(BusinessStatus.ORDER_STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        boolean result = updateById(order);

        if (result) {
            // 同步清除缓存（快速操作）
            redisUtil.delete(String.format(CacheKey.USER_ORDERS, userId));
            redisUtil.delete(String.format(CacheKey.ORDER_INFO, orderNo));

            // 异步：发送支付确认事件（库存确认由消费者异步执行，支持重试）
            PaymentConfirmedEvent paymentEvent = new PaymentConfirmedEvent();
            paymentEvent.setMessageId(idempotentUtil.generateMessageId());
            paymentEvent.setOrderId(order.getId());
            paymentEvent.setOrderNo(orderNo);
            paymentEvent.setUserId(userId);
            paymentEvent.setTrainId(order.getTrainId());
            paymentEvent.setTrainDate(order.getTrainDate());
            paymentEvent.setStartStation(order.getStartStation());
            paymentEvent.setEndStation(order.getEndStation());
            paymentEvent.setSeatType(order.getSeatType());
            paymentEvent.setCount(count);
            paymentEvent.setPayTimestamp(System.currentTimeMillis());
            rocketMQProducerService.sendPaymentConfirmedEvent(paymentEvent);

            logger.info("支付成功，已发送异步确认事件: orderNo={}, itemCount={}", orderNo, count);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundOrder(Long userId, String orderNo) {
        // 查询订单
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo)
                .eq(Order::getUserId, userId);

        Order order = getOne(wrapper);
        if (order == null) {
            throw new RuntimeException(ResponseCode.ORDER_NOT_FOUND.getMessage());
        }

        // 检查状态
        if (!order.getStatus().equals(BusinessStatus.ORDER_STATUS_PAID)) {
            throw new RuntimeException(ResponseCode.ORDER_CAN_NOT_REFUND.getMessage());
        }

        // 查询订单明细数量
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, order.getId());
        int count = Math.toIntExact(orderItemMapper.selectCount(itemWrapper));

        // 回滚库存（退票必须同步执行，保证数据一致性）
        trainService.rollbackStock(order.getTrainId(), order.getTrainDate().toString(),
                order.getStartStation(), order.getEndStation(), order.getSeatType(), count);

        // 更新订单状态
        order.setStatus(BusinessStatus.ORDER_STATUS_REFUNDED);
        boolean result = updateById(order);

        // 清除缓存
        redisUtil.delete(String.format(CacheKey.USER_ORDERS, userId));
        redisUtil.delete(String.format(CacheKey.ORDER_INFO, orderNo));

        return result;
    }

    @Override
    public List<Order> getUserOrders(Long userId) {
        // 先查缓存
        String cacheKey = String.format(CacheKey.USER_ORDERS, userId);
        List<Order> cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 查数据库
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt);

        List<Order> orders = list(wrapper);

        redisUtil.set(cacheKey, orders, 10, TimeUnit.MINUTES);

        return orders;
    }

    @Override
    public Order getOrderDetail(String orderNo) {
        // 先查缓存
        String cacheKey = String.format(CacheKey.ORDER_INFO, orderNo);
        Order cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 查数据库
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);

        Order order = getOne(wrapper);

        if (order != null) {
            redisUtil.set(cacheKey, order, 30, TimeUnit.MINUTES);
        }

        return order;
    }

    @Override
    public Page<Order> adminPage(String orderNo, String phone, Integer status, int page, int size) {
        Page<Order> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();

        // 订单号模糊查询
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            wrapper.like(Order::getOrderNo, "%" + orderNo.trim() + "%");
        }

        // 状态精确查询
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }

        // 用户手机号模糊查询
        if (phone != null && !phone.trim().isEmpty()) {
            // 通过手机号模糊查询用户ID列表
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.like(User::getPhone, "%" + phone.trim() + "%");
            List<Long> userIds = userService.list(userWrapper)
                    .stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                wrapper.in(Order::getUserId, userIds);
            } else {
                // 如果没有匹配的用户，确保返回空结果
                wrapper.eq(Order::getId, -1L);
            }
        }

        // 按创建时间倒序
        wrapper.orderByDesc(Order::getCreatedAt);

        return page(pageObj, wrapper);
    }
}