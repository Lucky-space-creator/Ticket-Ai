package com.ticket.aichat.tool;

import com.ticket.aichat.client.OrderQueryClient;
import com.ticket.aichat.client.TrainClient;
import com.ticket.aichat.client.UserReadFeignClient;
import com.ticket.aichat.service.ChatSessionService;
import com.ticket.aichat.service.HumanTransferPublisher;
import com.ticket.enums.ResponseCode;
import com.ticket.util.ResponseUtil;
import com.ticket.util.UserContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import feign.FeignException;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * AI 可调用的业务工具。
 * <p>写操作（下单/支付/退票/改资料/改乘客）在微服务场景下必须由用户经网关与领域服务显式确认，
 * 禁止由 LLM 自动触发；见 {@code docs/microservices/AI-GUARDRAILS.md}。</p>
 */
@Component
public class AIBusinessTool {

    private static final Logger log = LoggerFactory.getLogger(AIBusinessTool.class);

    @Resource
    private TrainClient trainClient;

    @Resource
    private OrderQueryClient orderQueryClient;

    @Resource
    private UserReadFeignClient userReadFeignClient;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private HumanTransferPublisher humanTransferPublisher;

    private static RuntimeException denyAutonomousWrite(String toolName) {
        return new IllegalStateException("策略禁止：AI 工具「" + toolName + "」不得自动执行写操作，请通过官方购票/个人中心完成。");
    }

    /**
     * 将 Feign 调用结果封装为工具返回值
     * @param result Feign 调用结果
     * @param actionLabel 操作标签
     * @return 工具返回值
     */
    private static Object unwrap(ResponseUtil.Result<?> result, String actionLabel) {
        if (result == null) {
            return Map.of("error", true, "message", actionLabel + "：无响应");
        }
        if (!ResponseCode.SUCCESS.getCode().equals(result.getCode())) {
            String msg = result.getMessage() != null ? result.getMessage() : "请求失败";
            return Map.of("error", true, "message", msg);
        }
        return result.getData() != null ? result.getData() : Map.of();
    }

    private Object feignCall(String op, Supplier<Object> supplier) {
        try {
            return supplier.get();
        } catch (FeignException e) {
            log.warn("Feign 调用失败 [{}]: status={} {}", op, e.status(), e.getMessage());
            return Map.of("error", true, "message", "下游服务暂时不可用（" + op + "），请稍后重试");
        } catch (Exception e) {
            log.warn("调用异常 [{}]: {}", op, e.getMessage());
            return Map.of("error", true, "message", e.getMessage());
        }
    }

    @Tool("查询车次信息，根据出发地、目的地和日期返回可用车次列表，注意：参数必须是日期字符串（如 '2025-12-31'），不是数据库ID。")
    public List<Object> searchTrains(
            @P(value = "出发地", required = true) String from,
            @P(value = "目的地", required = true) String to,
            @P(value = "出发日期", required = true) String date) {
        Object data = feignCall("searchTrains", () -> unwrap(
                trainClient.searchTrainsPublic(
                        from != null ? from.trim() : "",
                        to != null ? to.trim() : "",
                        date != null ? date.trim() : ""),
                "查询车次"));
        if (data instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) list;
            return cast;
        }
        if (data instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("error"))) {
            return List.of(data);
        }
        return data == null ? List.of() : List.of(data);
    }

    @Tool("获取车次详情，根据车次号返回详细信息。注意：参数必须是车次号字符串（如 'G1234'），不是数据库ID。")
    public Object getTrainDetail(@P(value = "车次号", required = true) String trainNo) {
        String no = trainNo != null ? trainNo.trim() : "";
        return feignCall("getTrainDetail", () -> unwrap(trainClient.getTrainByTrainNo(no), "车次详情"));
    }

    @Tool("购买车票，根据车次信息、乘客信息和座位类型下单，注意：不可缺少参数信息。")
    public Object createOrder(
            @P(value = "车次ID", required = true) Long trainId,
            @P(value = "出发日期", required = true) String trainDate,
            @P(value = "出发站", required = true) String startStation,
            @P(value = "到达站", required = true) String endStation,
            @P(value = "座位类型", required = true) Integer seatType,
            @P(value = "乘客姓名", required = true) String passengerNames,
            @P(value = "身份证号", required = true) String idCards) {
        throw denyAutonomousWrite("createOrder");
    }

    @Tool("支付订单，根据订单号完成支付，注意：订单号必须是字符串，不是数据库ID。")
    public boolean payOrder(@P(value = "订单号", required = true) String orderNo) {
        throw denyAutonomousWrite("payOrder");
    }

    @Tool("取消订单，根据订单号取消未支付的订单或退票")
    public boolean refundOrder(@P(value = "订单号", required = true) String orderNo) {
        throw denyAutonomousWrite("refundOrder");
    }

    @Tool("查询用户的所有订单")
    public List<Object> getUserOrders() {
        Object data = feignCall("getUserOrders", () -> unwrap(orderQueryClient.listMyOrders(), "我的订单"));
        if (data instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) list;
            return cast;
        }
        //如果返回返回的是Map，则返回一个List，包含这个Map，适配
        if (data instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("error"))) {
            return List.of(data);
        }
        return List.of();
    }

    @Tool("查询订单详情，根据订单号返回订单详细信息")
    public Object getOrderDetail(@P(value = "订单号", required = true) String orderNo) {
        String no = orderNo != null ? orderNo.trim() : "";
        return feignCall("getOrderDetail", () -> unwrap(orderQueryClient.getOrderDetail(no), "订单详情"));
    }

    @Tool("更新个人信息，包括真实姓名和身份证号")
    public boolean updateProfile(
            @P(value = "真实姓名", required = true) String realName,
            @P(value = "身份证号", required = true) String idCard) {
        throw denyAutonomousWrite("updateProfile");
    }

    @Tool("获取常用联系人列表")
    public List<Object> getPassengers() {
        Object data = feignCall("getPassengers", () -> unwrap(userReadFeignClient.listPassengers(), "常用联系人"));
        if (data instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) list;
            return cast;
        }
        if (data instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("error"))) {
            return List.of(data);
        }
        return List.of();
    }

    @Tool("添加常用联系人")
    public Object addPassenger(
            @P(value = "姓名", required = true) String name,
            @P(value = "身份证号", required = true) String idCard,
            @P(value = "手机号", required = true) String phone) {
        throw denyAutonomousWrite("addPassenger");
    }

    @Tool("删除常用联系人")
    public boolean deletePassenger(@P(value = "联系人ID", required = true) Long passengerId) {
        throw denyAutonomousWrite("deletePassenger");
    }

    @Tool("获取当前用户个人信息")
    public Map<String, String> getUserProfile() {
        Object data = feignCall("getUserProfile", () -> unwrap(userReadFeignClient.getProfile(), "个人信息"));
        if (!(data instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        if (Boolean.TRUE.equals(raw.get("error"))) {
            return Map.of("error", raw.get("message") != null ? String.valueOf(raw.get("message")) : "失败");
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            out.put(String.valueOf(e.getKey()), e.getValue() != null ? String.valueOf(e.getValue()) : "");
        }
        return out;
    }

    @Tool("转接人工客服，将当前会话转接给人工客服，可提供转接原因")
    public String transferToHuman(@P(value = "转接原因", required = false) String reason) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return "请先登录后再请求人工客服";
        }
        String sessionId = chatSessionService.getOrCreateAiOnlySessionId(userId);
        return humanTransferPublisher.publish(userId, sessionId, reason);
    }
}
