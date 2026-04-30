package com.ticket.aichat.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI业务工具类（占位符）
 * 在aichat-service中，由于微服务拆分，实际业务工具需要通过Feign调用其他服务
 * 这里提供基本的工具接口，确保编译通过
 */
@Component
public class AIBusinessTool {

    private static final Logger log = LoggerFactory.getLogger(AIBusinessTool.class);

    @Tool("查询车次信息，根据出发地、目的地和日期返回可用车次列表")
    public List<Object> searchTrains(
            @P(value = "出发地", required = true) String from,
            @P(value = "目的地", required = true) String to,
            @P(value = "出发日期", required = true) String date) {
        log.warn("searchTrains工具未实现，需要通过Feign调用train-service");
        return List.of();
    }

    @Tool("获取车次详情，根据车次号返回详细信息。注意：参数必须是车次号字符串（如 'G1234'），不是数据库ID。")
    public Object getTrainDetail(@P(value = "车次号", required = true) String trainNo) {
        log.warn("getTrainDetail工具未实现，需要通过Feign调用train-service");
        return null;
    }

    @Tool("购买车票，根据车次信息、乘客信息和座位类型下单")
    public Object createOrder(
            @P(value = "车次ID", required = true) Long trainId,
            @P(value = "出发日期", required = true) String trainDate,
            @P(value = "出发站", required = true) String startStation,
            @P(value = "到达站", required = true) String endStation,
            @P(value = "座位类型", required = true) Integer seatType,
            @P(value = "乘客姓名", required = true) String passengerNames,
            @P(value = "身份证号", required = true) String idCards) {
        log.warn("createOrder工具未实现，需要通过Feign调用order-service");
        return null;
    }

    @Tool("支付订单，根据订单号完成支付")
    public boolean payOrder(@P(value = "订单号", required = true) String orderNo) {
        log.warn("payOrder工具未实现，需要通过Feign调用order-service");
        return false;
    }

    @Tool("取消订单，根据订单号取消未支付的订单或退票")
    public boolean refundOrder(@P(value = "订单号", required = true) String orderNo) {
        log.warn("refundOrder工具未实现，需要通过Feign调用order-service");
        return false;
    }

    @Tool("查询用户的所有订单")
    public List<Object> getUserOrders() {
        log.warn("getUserOrders工具未实现，需要通过Feign调用order-service");
        return List.of();
    }

    @Tool("查询订单详情，根据订单号返回订单详细信息")
    public Object getOrderDetail(@P(value = "订单号", required = true) String orderNo) {
        log.warn("getOrderDetail工具未实现，需要通过Feign调用order-service");
        return null;
    }

    @Tool("更新个人信息，包括真实姓名和身份证号")
    public boolean updateProfile(
            @P(value = "真实姓名", required = true) String realName,
            @P(value = "身份证号", required = true) String idCard) {
        log.warn("updateProfile工具未实现，需要通过Feign调用user-service");
        return false;
    }

    @Tool("获取常用联系人列表")
    public List<Object> getPassengers() {
        log.warn("getPassengers工具未实现，需要通过Feign调用user-service");
        return List.of();
    }

    @Tool("添加常用联系人")
    public Object addPassenger(
            @P(value = "姓名", required = true) String name,
            @P(value = "身份证号", required = true) String idCard,
            @P(value = "手机号", required = true) String phone) {
        log.warn("addPassenger工具未实现，需要通过Feign调用user-service");
        return null;
    }

    @Tool("删除常用联系人")
    public boolean deletePassenger(@P(value = "联系人ID", required = true) Long passengerId) {
        log.warn("deletePassenger工具未实现，需要通过Feign调用user-service");
        return false;
    }

    @Tool("获取当前用户个人信息")
    public java.util.Map<String, String> getUserProfile() {
        log.warn("getUserProfile工具未实现，需要通过Feign调用user-service");
        return java.util.Map.of();
    }

    @Tool("转接人工客服，将当前会话转接给人工客服，可提供转接原因")
    public String transferToHuman(@P(value = "转接原因", required = false) String reason) {
        log.warn("transferToHuman工具未实现");
        return "转人工客服功能暂不可用，请通过其他方式联系客服";
    }
}