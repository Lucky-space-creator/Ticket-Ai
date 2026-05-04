package com.ticket.aichat.client;

import com.ticket.util.ResponseUtil;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 订单只读 Feign（与 order-service {@code OrderController} 路径对齐）
 */
@FeignClient(name = "order-service", contextId = "aichatOrderQueryClient")
public interface OrderQueryClient {

    @GetMapping("/api/orders")
    ResponseUtil.Result<?> listMyOrders();

    @GetMapping("/api/orders/{orderNo}")
    ResponseUtil.Result<?> getOrderDetail(@PathVariable("orderNo") String orderNo);
}
