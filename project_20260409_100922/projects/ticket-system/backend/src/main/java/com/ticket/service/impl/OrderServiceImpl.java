package com.ticket.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.Train;
import com.ticket.entity.User;
import com.ticket.enums.BusinessStatus;
import com.ticket.enums.CacheKey;
import com.ticket.enums.ResponseCode;
import com.ticket.mapper.OrderItemMapper;
import com.ticket.mapper.OrderMapper;
import com.ticket.service.OrderService;
import com.ticket.service.TrainService;
import com.ticket.service.UserService;
import com.ticket.util.RedisUtil;
import com.ticket.util.SnowflakeIdUtil;
import jakarta.annotation.Resource;
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
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    private TrainService trainService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private UserService userService;

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

        // 2. 扣减库存
        trainService.deductStock(trainId, trainDate, startStation, endStation, seatType, items.size());

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

        // 6. 清除用户订单缓存
        redisUtil.delete(String.format(CacheKey.USER_ORDERS, userId));

        return order;
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

        // 更新状态
        order.setStatus(BusinessStatus.ORDER_STATUS_PAID);
        order.setPayTime(LocalDateTime.now());

        boolean result = updateById(order);

        // 清除缓存
        redisUtil.delete(String.format(CacheKey.USER_ORDERS, userId));
        redisUtil.delete(String.format(CacheKey.ORDER_INFO, orderNo));

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
        if (order.getStatus() != BusinessStatus.ORDER_STATUS_PAID) {
            throw new RuntimeException(ResponseCode.ORDER_CAN_NOT_REFUND.getMessage());
        }

        // 查询订单明细数量
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(OrderItem::getOrderId, order.getId());
        int count = Math.toIntExact(orderItemMapper.selectCount(itemWrapper));

        // 回滚库存
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
