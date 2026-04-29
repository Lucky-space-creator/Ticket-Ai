package com.ticket.aichat.client;

import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单服务 Feign 客户端
 */
@FeignClient(name = "order-service", path = "/api/order")
public interface OrderClient {

    /**
     * 创建订单
     */
    @PostMapping("/create")
    Order createOrder(@RequestParam("userId") Long userId,
                      @RequestParam("trainId") Long trainId,
                      @RequestParam("trainDate") String trainDate,
                      @RequestParam("startStation") String startStation,
                      @RequestParam("endStation") String endStation,
                      @RequestParam("seatType") Integer seatType,
                      @RequestBody List<OrderItem> items);

    /**
     * 支付订单
     */
    @PostMapping("/pay")
    boolean payOrder(@RequestParam("userId") Long userId,
                     @RequestParam("orderNo") String orderNo);

    /**
     * 退票
     */
    @PostMapping("/refund")
    boolean refundOrder(@RequestParam("userId") Long userId,
                        @RequestParam("orderNo") String orderNo);

    /**
     * 获取用户订单列表
     */
    @GetMapping("/userOrders")
    List<Order> getUserOrders(@RequestParam("userId") Long userId);

    /**
     * 获取订单详情
     */
    @GetMapping("/detail")
    Order getOrderDetail(@RequestParam("orderNo") String orderNo);
}