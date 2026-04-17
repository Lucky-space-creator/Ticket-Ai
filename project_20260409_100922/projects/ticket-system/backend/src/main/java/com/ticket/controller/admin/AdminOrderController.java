package com.ticket.controller.admin;

import com.ticket.entity.Order;
import com.ticket.service.OrderService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端订单控制器
 */
@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin(origins = "*")
public class AdminOrderController {

    @Resource
    private OrderService orderService;

    /**
     * 获取所有订单列表
     */
    @GetMapping
    public ResponseUtil.Result<List<Order>> list() {
        // 这里需要实现获取所有订单的逻辑，暂时返回空列表
        List<Order> orders = orderService.list();
        return ResponseUtil.success(orders);
    }

    /**
     * 退票操作
     */
    @PostMapping("/{orderNo}/refund")
    public ResponseUtil.Result<?> refund(@PathVariable String orderNo) {
        // 管理端退票逻辑，暂时返回成功
        return ResponseUtil.success("退票成功");
    }
}