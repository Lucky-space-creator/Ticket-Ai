package com.ticket.controller.admin;

import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.User;
import com.ticket.service.OrderService;
import com.ticket.service.UserService;
import com.ticket.util.CryptoUtil;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Resource
    private UserService userService;

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
        fillUserPhone(pageResult.getRecords());
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
        // 查询订单明细并脱敏身份证号
        List<OrderItem> items = orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId())
        );
        items.forEach(item -> item.setIdCard(maskIdCard(item.getIdCard())));
        order.setItems(items);
        // 填充用户手机号
        fillUserPhone(List.of(order));
        return ResponseUtil.success(order);
    }

    /**
     * 退票操作
     */
    @PostMapping("/{orderNo}/refund")
    public ResponseUtil.Result<?> refund(@PathVariable String orderNo) {
        Order order = orderService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                        .eq(Order::getOrderNo, orderNo)
        );
        if (order == null) {
            return ResponseUtil.error("订单不存在");
        }
        if (order.getStatus() != 1) {
            return ResponseUtil.error("只有已支付的订单才能退票");
        }
        boolean result = orderService.refundOrder(order.getUserId(), orderNo);
        if (result) {
            return ResponseUtil.success("退票成功");
        }
        return ResponseUtil.error("退票失败");
    }

    /**
     * 批量填充用户手机号
     */
    private void fillUserPhone(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<Long> userIds = orders.stream()
                .map(Order::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> phoneMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getPhone, (a, b) -> a));
        orders.forEach(o -> o.setUserPhone(phoneMap.get(o.getUserId())));
    }

    /**
     * 身份证号脱敏：解密后显示前3后4位，中间用*代替
     */
    private String maskIdCard(String encryptedIdCard) {
        if (encryptedIdCard == null || encryptedIdCard.isEmpty()) {
            return encryptedIdCard;
        }
        try {
            String idCard = CryptoUtil.decrypt(encryptedIdCard);
            if (idCard == null || idCard.length() < 8) {
                return "****";
            }
            return idCard.substring(0, 3) + "****" + idCard.substring(idCard.length() - 4);
        } catch (Exception e) {
            return "****";
        }
    }
}