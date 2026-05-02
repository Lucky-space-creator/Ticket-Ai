package com.ticket.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticket.admin.service.AdminUserService;
import com.ticket.admin.mapper.OrderItemMapper;
import com.ticket.admin.service.OrderService;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.User;
import com.ticket.util.CryptoUtil;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin(origins = "*")
public class AdminOrderController {

    @Resource
    private OrderService orderService;

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    private AdminUserService adminUserService;

    /**
     * 订单列表
     * @param orderNo 订单编号
     * @param phone 用户手机号
     * @param status 订单状态
     * @param page 当前页数
     * @param size 每页数量
     * @return 订单列表
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
        // 填充用户手机号
        fillUserPhone(pageResult.getRecords());
        return ResponseUtil.success(pageResult);
    }

    @GetMapping("/{orderNo}")
    public ResponseUtil.Result<Order> detail(@PathVariable("orderNo") String orderNo) {
        Order order = orderService.getOrderDetail(orderNo);
        if (order == null) {
            return ResponseUtil.error("订单不存在");
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())
        );
        items.forEach(item -> item.setIdCard(maskIdCard(item.getIdCard())));
        order.setItems(items);
        fillUserPhone(List.of(order));
        return ResponseUtil.success(order);
    }

    @PostMapping("/{orderNo}/refund")
    public ResponseUtil.Result<?> refund(@PathVariable("orderNo") String orderNo) {
        Order order = orderService.getOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) {
            return ResponseUtil.error("订单不存在");
        }
        if (order.getStatus() != 1) {
            return ResponseUtil.error("订单状态不支持退票");
        }
        boolean result = orderService.refundOrder(order.getUserId(), orderNo);
        if (result) {
            return ResponseUtil.success("退票成功");
        }
        return ResponseUtil.error("退票失败");
    }

    private void fillUserPhone(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Set<Long> userIds = orders.stream()
                .map(Order::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> phoneMap = adminUserService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getPhone, (a, b) -> a));
        orders.forEach(o -> o.setUserPhone(phoneMap.get(o.getUserId())));
    }

    // 身份证脱敏
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
