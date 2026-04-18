package com.ticket.controller.admin;

import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.service.OrderService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticket.mapper.OrderItemMapper;

/**
 * 管理端订单控制器
 */
@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin(origins = "*")
public class AdminOrderController {

    @Resource
    private OrderService orderService;

    @Resource
    private OrderItemMapper orderItemMapper;

    /**
     * 获取所有订单列表（分页+模糊查询）
     */
    @GetMapping
    public ResponseUtil.Result<Page<Order>> list(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<Order> pageResult = orderService.adminPage(orderNo, phone, status, page, size);
        return ResponseUtil.success(pageResult);
    }

    /**
     * 获取订单详情（管理端）
     */
    @GetMapping("/{orderNo}")
    public ResponseUtil.Result<Order> detail(@PathVariable String orderNo) {
        Order order = orderService.getOrderDetail(orderNo);
        if (order == null) {
            return ResponseUtil.error("订单不存在");
        }
        // 查询订单明细
        List<OrderItem> items = orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        order.setItems(items);
        return ResponseUtil.success(order);
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