package com.ticket.order.controller;

import com.ticket.dto.internal.OrderRefundCommand;
import com.ticket.order.service.OrderService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部退票入口：供 admin-service 通过 Feign 调用，保持库存与订单状态在订单限界上下文中一致。
 */
@RestController
@RequestMapping("/api/internal/orders")
public class InternalOrderRefundController {

    @Resource
    private OrderService orderService;

    @PostMapping("/refund")
    public ResponseUtil.Result<Void> refund(@RequestBody OrderRefundCommand command) {
        try {
            orderService.refundOrder(command.getUserId(), command.getOrderNo());
            return ResponseUtil.success("退票成功");
        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }
}
