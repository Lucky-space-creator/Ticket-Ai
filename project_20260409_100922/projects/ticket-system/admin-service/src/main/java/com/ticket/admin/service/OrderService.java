package com.ticket.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.Order;

/**
 * 管理端订单应用服务端口：仅暴露后台查询与退票编排，不包含用户侧下单/支付（归属 order-service）。
 */
public interface OrderService extends IService<Order> {

    Page<Order> adminPage(String orderNo, String phone, Integer status, int page, int size);

    Order getOrderDetail(String orderNo);

    boolean refundOrder(Long userId, String orderNo);
}
