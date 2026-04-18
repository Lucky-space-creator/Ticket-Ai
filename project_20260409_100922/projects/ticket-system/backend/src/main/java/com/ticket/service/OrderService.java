package com.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;

import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService extends IService<Order> {

    /**
     * 创建订单
     */
    Order createOrder(Long userId, Long trainId, String trainDate, String startStation, String endStation, Integer seatType, List<OrderItem> items);

    /**
     * 支付订单
     */
    boolean payOrder(Long userId, String orderNo);

    /**
     * 退票
     */
    boolean refundOrder(Long userId, String orderNo);

    /**
     * 获取用户订单列表
     */
    List<Order> getUserOrders(Long userId);

    /**
     * 获取订单详情
     */
    Order getOrderDetail(String orderNo);

    /**
     * 管理端订单分页查询
     */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Order> adminPage(String orderNo, String phone, Integer status, int page, int size);
}
