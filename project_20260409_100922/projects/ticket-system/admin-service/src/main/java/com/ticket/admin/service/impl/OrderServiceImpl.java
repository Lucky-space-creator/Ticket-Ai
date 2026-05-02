package com.ticket.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.admin.infrastructure.client.OrderRefundFeignClient;
import com.ticket.admin.mapper.OrderMapper;
import com.ticket.admin.mapper.UserMapper;
import com.ticket.admin.service.OrderService;
import com.ticket.dto.internal.OrderRefundCommand;
import com.ticket.entity.Order;
import com.ticket.entity.User;
import com.ticket.enums.CacheKey;
import com.ticket.enums.ResponseCode;
import com.ticket.util.RedisUtil;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private UserMapper userMapper;

    @Resource
    private OrderRefundFeignClient orderRefundFeignClient;

    @Override
    public Page<Order> adminPage(String orderNo, String phone, Integer status, int page, int size) {
        Page<Order> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            wrapper.like(Order::getOrderNo, "%" + orderNo.trim() + "%");
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        if (phone != null && !phone.trim().isEmpty()) {
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.like(User::getPhone, "%" + phone.trim() + "%");
            List<Long> userIds = userMapper.selectList(userWrapper).stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                wrapper.in(Order::getUserId, userIds);
            } else {
                wrapper.eq(Order::getId, -1L);
            }
        }
        wrapper.orderByDesc(Order::getCreatedAt);
        return page(pageObj, wrapper);
    }

    @Override
    public Order getOrderDetail(String orderNo) {
        String cacheKey = String.format(CacheKey.ORDER_INFO, orderNo);
        Order cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = getOne(wrapper);
        if (order != null) {
            redisUtil.set(cacheKey, order, 30, TimeUnit.MINUTES);
        }
        return order;
    }

    @Override
    public boolean refundOrder(Long userId, String orderNo) {
        ResponseUtil.Result<Void> result = orderRefundFeignClient.refund(new OrderRefundCommand(orderNo, userId));
        return result != null && Objects.equals(result.getCode(), ResponseCode.SUCCESS.getCode());
    }
}
