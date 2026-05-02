package com.ticket.aichat.client;

import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单服务 Feign 客户端
 * 作用: 调用订单服务创建、支付、退票、获取用户订单列表、获取订单详情，为 AI 模型提供订单信息的工具
 */
@FeignClient(name = "order-service", path = "/api/order")
public interface OrderClient {


    /**
     * 创建订单
     * @param userId 用户ID
     * @param trainId 车次ID
     * @param trainDate 车次日期
     * @param startStation 出发站点
     * @param endStation 终点站
     * @param seatType 座位类型
     * @param items 订单项列表
     * @return 订单
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
     * @param userId 用户ID
     * @param orderNo 订单号
     * @return 是否支付成功
     */
    @PostMapping("/pay")
    boolean payOrder(@RequestParam("userId") Long userId,
                     @RequestParam("orderNo") String orderNo);

    /**
     * 退票
     * @param userId 用户ID
     * @param orderNo 订单
     * @return 是否退票成功
     */
    @PostMapping("/refund")
    boolean refundOrder(@RequestParam("userId") Long userId,
                        @RequestParam("orderNo") String orderNo);

    /**
     * 获取用户订单列表
     * @param userId 用户ID
     * @return 订单列表
     */
    @GetMapping("/userOrders")
    List<Order> getUserOrders(@RequestParam("userId") Long userId);

    /**
     * 获取订单详情
     * @param orderNo 订单号
     * @return 订单
     */
    @GetMapping("/detail")
    Order getOrderDetail(@RequestParam("orderNo") String orderNo);
}