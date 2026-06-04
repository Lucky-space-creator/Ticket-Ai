package com.ticket.aichat.tool;

import com.ticket.aichat.client.OrderQueryClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 订单工具：查询订单、引导下单/退票/支付。
 * <p>
 * 写操作（下单/支付/退票）由策略禁止自动执行，供 OrderAgent 使用。
 */
@Component
public class OrderTools {

    @Resource
    private OrderQueryClient orderQueryClient;

    @Tool("购买车票，根据车次信息、乘客信息和座位类型下单，注意：不可缺少参数信息。")
    public Object createOrder(
            @P(value = "车次ID", required = true) Long trainId,
            @P(value = "出发日期", required = true) String trainDate,
            @P(value = "出发站", required = true) String startStation,
            @P(value = "到达站", required = true) String endStation,
            @P(value = "座位类型", required = true) Integer seatType,
            @P(value = "乘客姓名", required = true) String passengerNames,
            @P(value = "身份证号", required = true) String idCards) {
        throw ToolHelperUtil.denyAutonomousWrite("createOrder");
    }

    @Tool("支付订单，根据订单号完成支付，注意：订单号必须是字符串，不是数据库ID。")
    public boolean payOrder(@P(value = "订单号", required = true) String orderNo) {
        throw ToolHelperUtil.denyAutonomousWrite("payOrder");
    }

    @Tool("取消订单，根据订单号取消未支付的订单或退票")
    public boolean refundOrder(@P(value = "订单号", required = true) String orderNo) {
        throw ToolHelperUtil.denyAutonomousWrite("refundOrder");
    }

    @Tool("查询用户的所有订单")
    public List<Object> getUserOrders() {
        Object data = ToolHelperUtil.feignCall("getUserOrders",
                () -> ToolHelperUtil.unwrap(orderQueryClient.listMyOrders(), "我的订单"));
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

    @Tool("查询订单详情，根据订单号返回订单详细信息")
    public Object getOrderDetail(@P(value = "订单号", required = true) String orderNo) {
        String no = orderNo != null ? orderNo.trim() : "";
        return ToolHelperUtil.feignCall("getOrderDetail",
                () -> ToolHelperUtil.unwrap(orderQueryClient.getOrderDetail(no), "订单详情"));
    }
}
