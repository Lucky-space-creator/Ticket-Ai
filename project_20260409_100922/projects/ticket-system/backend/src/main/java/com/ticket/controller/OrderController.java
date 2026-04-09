package com.ticket.controller;

import com.ticket.enums.ResponseCode;
import com.ticket.dto.CreateOrderRequest;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.service.OrderService;
import com.ticket.util.CryptoUtil;
import com.ticket.util.JwtUtil;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    @Resource
    private OrderService orderService;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        return null;
    }

    /**
     * 创建订单
     */
    @PostMapping
    public ResponseUtil.Result<?> createOrder(@RequestBody CreateOrderRequest request, HttpServletRequest httpRequest) {
        try {
            Long userId = getCurrentUserId(httpRequest);
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }

            // 转换订单明细
            List<OrderItem> items = new ArrayList<>();
            if (request.getItems() != null) {
                for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
                    OrderItem item = new OrderItem();
                    item.setPassengerName(itemRequest.getPassengerName());
                    item.setIdCard(CryptoUtil.encrypt(itemRequest.getIdCard()));
                    // 查询票价
                    item.setPrice(java.math.BigDecimal.valueOf(100)); // 这里应该从余票表查询
                    items.add(item);
                }
            }

            Order order = orderService.createOrder(
                    userId,
                    request.getTrainId(),
                    request.getTrainDate(),
                    request.getStartStation(),
                    request.getEndStation(),
                    request.getSeatType(),
                    items
            );

            return ResponseUtil.success("下单成功", order);
        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 支付订单
     */
    @PostMapping("/{orderNo}/pay")
    public ResponseUtil.Result<?> payOrder(@PathVariable String orderNo, HttpServletRequest httpRequest) {
        try {
            Long userId = getCurrentUserId(httpRequest);
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }

            boolean result = orderService.payOrder(userId, orderNo);

            if (result) {
                return ResponseUtil.success("支付成功");
            } else {
                return ResponseUtil.error("支付失败");
            }
        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 退票
     */
    @PostMapping("/{orderNo}/refund")
    public ResponseUtil.Result<?> refundOrder(@PathVariable String orderNo, HttpServletRequest httpRequest) {
        try {
            Long userId = getCurrentUserId(httpRequest);
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }

            boolean result = orderService.refundOrder(userId, orderNo);

            if (result) {
                return ResponseUtil.success("退票成功");
            } else {
                return ResponseUtil.error("退票失败");
            }
        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 获取订单列表
     */
    @GetMapping
    public ResponseUtil.Result<?> getOrders(HttpServletRequest httpRequest) {
        try {
            Long userId = getCurrentUserId(httpRequest);
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }

            List<Order> orders = orderService.getUserOrders(userId);

            return ResponseUtil.success(orders);
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/{orderNo}")
    public ResponseUtil.Result<?> getOrderDetail(@PathVariable String orderNo, HttpServletRequest httpRequest) {
        try {
            Long userId = getCurrentUserId(httpRequest);
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }

            Order order = orderService.getOrderDetail(orderNo);

            // 权限校验
            if (!order.getUserId().equals(userId)) {
                return ResponseUtil.error(ResponseCode.FORBIDDEN);
            }

            return ResponseUtil.success(order);
        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }
}
