package com.ticket.order.controller;

import com.ticket.dto.CreateOrderRequest;
import com.ticket.dto.RouteLeg;
import com.ticket.dto.mq.OrderQueueRequest;
import com.ticket.dto.train.RouteValidationRequest;
import com.ticket.dto.train.RouteValidationResult;
import com.ticket.entity.Order;
import com.ticket.enums.ResponseCode;
import com.ticket.enums.RouteType;
import com.ticket.order.client.TrainRouteValidationFeignClient;
import com.ticket.order.integration.TrainOrderGateway;
import com.ticket.order.service.OrderQueueService;
import com.ticket.order.service.OrderService;
import com.ticket.order.service.impl.OrderQueueServiceImpl;
import com.ticket.util.CryptoUtil;
import com.ticket.util.ResponseUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单控制器（MQ异步削峰版本）
 * createOrder: 快速入队模式（Redis预扣+MQ入队→立即返回requestId）
 * queryQueueStatus: 轮询排队结果
 * 其余接口委托给原OrderService处理（支付/退票/查询不变）
 */
@RestController
@RequestMapping("/api/orders")
// 允许跨域请求
@CrossOrigin(origins = "*")
public class OrderController {

    @Resource
    private OrderQueueService orderQueueService;

    @Resource
    private OrderService orderService;

    @Resource
    private TrainOrderGateway trainOrderGateway;

    @Resource
    private TrainRouteValidationFeignClient trainRouteValidationFeignClient;

    /**
     * 创建订单（快速入队模式 — MQ削峰核心改造）
     *
     * 改造前：同步等待 DB写入完成（耗时50~200ms+）
     * 改造后：Redis预扣(1~5ms) + MQ入队(1ms) → 立即返回（总耗时<10ms）
     *
     * 前端流程：
     *   POST /api/orders → 返回 { requestId, status:"PROCESSING" }
     *   → 轮询 GET /api/orders/queue/{requestId}
     *   → SUCCESS时拿到orderNo → 跳转支付页面
     *   idempotencyKey 用于幂等控制 前端通过uuid生成 后端通过redis存储
     */
    @PostMapping
    public ResponseUtil.Result<?> createOrder(
            @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }

            // 1. 参数校验
            validateCreateOrderRequest(request);

            boolean multi = request.getLegs() != null && !request.getLegs().isEmpty();

            BigDecimal seatPrice;
            List<RouteLeg> validatedLegs = null;

            if (multi) {
                RouteValidationRequest vr = new RouteValidationRequest();
                vr.setTrainDate(request.getTrainDate());
                vr.setSeatType(request.getSeatType());
                vr.setStartStation(request.getStartStation());
                vr.setEndStation(request.getEndStation());
                vr.setRouteSku(request.getRouteSku());
                vr.setRouteType(request.getRouteType());
                vr.setLegs(request.getLegs());
                com.ticket.util.ResponseUtil.Result<RouteValidationResult> res = trainRouteValidationFeignClient.validate(vr);
                if (res == null || res.getCode() == null
                        || !res.getCode().equals(ResponseCode.SUCCESS.getCode())
                        || res.getData() == null) {
                    return ResponseUtil.error(res != null ? res.getMessage() : "行程校验失败");
                }
                RouteValidationResult data = res.getData();
                validatedLegs = data.getLegs();
                seatPrice = data.getTotalPricePerPassenger();
            } else {
                seatPrice = trainOrderGateway.getSeatPrice(
                        request.getTrainId(), request.getTrainDate(),
                        request.getStartStation(), request.getEndStation(),
                        request.getSeatType());
            }

            // 3. 构造乘客列表（含加密身份证和票价）
            List<OrderQueueRequest.PassengerItem> items = buildPassengerItems(request.getItems(), seatPrice);

            // 4. 构造排队请求体
            OrderQueueRequest queueRequest = new OrderQueueRequest();
            queueRequest.setUserId(userId);
            queueRequest.setTrainId(multi ? validatedLegs.get(0).getSegmentId() : request.getTrainId());
            queueRequest.setTrainDate(request.getTrainDate());
            queueRequest.setStartStation(request.getStartStation());
            queueRequest.setEndStation(request.getEndStation());
            queueRequest.setSeatType(request.getSeatType());
            if (multi) {
                queueRequest.setLegs(validatedLegs);
                String canonicalSku = validatedLegs.stream()
                        .map(l -> String.valueOf(l.getSegmentId()))
                        .collect(java.util.stream.Collectors.joining("-"));
                if (request.getRouteSku() != null && !request.getRouteSku().isBlank()
                        && !canonicalSku.equals(request.getRouteSku().trim())) {
                    throw new RuntimeException("route_sku 与线段序列不一致");
                }
                queueRequest.setRouteSku(canonicalSku);
                queueRequest.setRouteType(request.getRouteType() != null && !request.getRouteType().isBlank()
                        ? request.getRouteType() : inferRouteType(validatedLegs));
            }
            queueRequest.setItems(items);
            queueRequest.setClientIp(getClientIp(httpRequest));
            queueRequest.setEnqueueTime(System.currentTimeMillis());

            // 5. 入队：Redis预扣 + MQ；入队失败时由 OrderQueueServiceImpl 回滚预占，不降级同步写单
            String requestId = orderQueueService.enqueue(queueRequest, idempotencyKey);

            // 6. 异步模式：立即返回 PROCESSING 状态（前端开始轮询）
            Map<String, Object> resultData = new HashMap<>(4);
            resultData.put("requestId", requestId);
            resultData.put("status", "PROCESSING");
            resultData.put("message", "订单正在处理中，请稍候...");

            return ResponseUtil.success("下单请求已提交", resultData);

        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /** 支付订单（委托给原OrderService） */
    @PostMapping("/{orderNo}/pay")
    public ResponseUtil.Result<?> payOrder(@PathVariable("orderNo") String orderNo) {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }
            boolean result = orderService.payOrder(userId, orderNo);
            return result ? ResponseUtil.success("支付成功") : ResponseUtil.error("支付失败");
        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /** 退票（委托给原OrderService） */
    @PostMapping("/{orderNo}/refund")
    public ResponseUtil.Result<?> refundOrder(@PathVariable String orderNo) {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }
            boolean result = orderService.refundOrder(userId, orderNo);
            return result ? ResponseUtil.success("退票成功") : ResponseUtil.error("退票失败");
        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 轮询排队结果（路径必须与 {@code /api/orders/{orderNo}} 同控制器声明，避免部分环境下映射解析异常）
     */
    @GetMapping("/queue/{requestId}")
    public ResponseUtil.Result<?> queryQueueStatus(@PathVariable("requestId") String requestId) {
        try {
            int status = orderQueueService.queryStatus(requestId);

            Map<String, Object> result = new HashMap<>(6);
            result.put("requestId", requestId);

            switch (status) {
                case OrderQueueServiceImpl.STATUS_PROCESSING:
                    result.put("status", "PROCESSING");
                    result.put("message", "订单正在处理中...");
                    break;
                case OrderQueueServiceImpl.STATUS_SUCCESS:
                    result.put("status", "SUCCESS");
                    result.put("orderNo", orderQueueService.getOrderNo(requestId));
                    result.put("message", "下单成功");
                    break;
                case OrderQueueServiceImpl.STATUS_FAILED:
                    result.put("status", "FAILED");
                    String errMsg = orderQueueService.getErrorMessage(requestId);
                    result.put("errorMessage", errMsg);
                    result.put("message", "下单失败: " + (errMsg != null ? errMsg : ""));
                    break;
                default:
                    result.put("status", "EXPIRED");
                    result.put("message", "查询结果已过期，请重新下单");
                    break;
            }

            return ResponseUtil.success(result);
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /** 获取订单列表（委托给原OrderService） */
    @GetMapping
    public ResponseUtil.Result<?> getOrders() {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }
            List<Order> orders = orderService.getUserOrders(userId);
            return ResponseUtil.success(orders);
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /** 获取订单详情（委托给原OrderService） */
    @GetMapping("/{orderNo}")
    public ResponseUtil.Result<?> getOrderDetail(@PathVariable("orderNo") String orderNo) {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }
            Order order = orderService.getOrderDetail(orderNo);
            if (order == null) {
                return ResponseUtil.error(ResponseCode.ORDER_NOT_FOUND);
            }
            if (!order.getUserId().equals(userId)) {
                return ResponseUtil.error(ResponseCode.FORBIDDEN);
            }
            return ResponseUtil.success(order);
        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    // ==================== 私有工具方法 ====================

    private void validateCreateOrderRequest(CreateOrderRequest request) {
        boolean multi = request.getLegs() != null && !request.getLegs().isEmpty();
        if (multi) {
            if (request.getRouteSku() == null || request.getRouteSku().isBlank()) {
                throw new RuntimeException("route_sku 不能为空");
            }
            if (request.getLegs().get(0) == null || request.getLegs().get(0).getSegmentId() == null) {
                throw new RuntimeException("首节线段ID不能为空");
            }
        } else if (request.getTrainId() == null) {
            throw new RuntimeException("车次ID不能为空");
        }
        if (request.getTrainDate() == null || request.getTrainDate().isEmpty()) {
            throw new RuntimeException("乘车日期不能为空");
        }
        if (request.getStartStation() == null || request.getStartStation().isEmpty()) {
            throw new RuntimeException("出发站不能为空");
        }
        if (request.getEndStation() == null || request.getEndStation().isEmpty()) {
            throw new RuntimeException("到达站不能为空");
        }
        if (request.getSeatType() == null) {
            throw new RuntimeException("席别不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("至少需要一个乘客");
        }
        if (request.getItems().size() > 5) {
            throw new RuntimeException("单次最多购买5张车票");
        }
    }

    private static String inferRouteType(List<RouteLeg> legs) {
        if (legs == null || legs.isEmpty()) {
            return RouteType.SINGLE;
        }
        if (legs.size() == 1) {
            return RouteType.SINGLE;
        }
        String tn0 = legs.get(0).getTrainNo();
        boolean allSame = true;
        for (RouteLeg l : legs) {
            if (l.getTrainNo() == null || !l.getTrainNo().equals(tn0)) {
                allSame = false;
                break;
            }
        }
        return allSame ? RouteType.DIRECT : RouteType.TRANSFER;
    }

    private List<OrderQueueRequest.PassengerItem> buildPassengerItems(List<CreateOrderRequest.OrderItemRequest> itemRequests, BigDecimal price) {
        List<OrderQueueRequest.PassengerItem> items = new ArrayList<>();
        for (CreateOrderRequest.OrderItemRequest itemReq : itemRequests) {
            String name = itemReq.getPassengerName();
            String idCard = itemReq.getIdCard();
            if (name == null || name.trim().isEmpty()) {
                throw new RuntimeException("乘客姓名不能为空");
            }
            if (idCard == null || idCard.trim().isEmpty()) {
                throw new RuntimeException("身份证号不能为空");
            }
            OrderQueueRequest.PassengerItem item = new OrderQueueRequest.PassengerItem();
            item.setPassengerName(name.trim());
            item.setIdCard(CryptoUtil.encrypt(idCard.trim()));
            item.setPrice(price);
            items.add(item);
        }
        return items;
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty()) {
                return ip.contains(",") ? ip.split(",")[0].trim() : ip.trim();
            }
        }
        return request.getRemoteAddr();
    }
}