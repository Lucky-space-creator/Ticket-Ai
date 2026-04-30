package com.ticket.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI调用业务工具类
 * 将所有可暴露的API整合起来，形成一个业务工具类，可调用具体的业务
 * 每个方法使用 @Tool 注解，可以被AI自动调用
 * 注意：这个类是空的占位符，需要在各个微服务中具体实现
 */
@Slf4j
@Component
public class AIBusinessTool {
    
    /**
     * 查询车次信息
     * @param from 出发城市
     * @param to 到达城市
     * @param date 出发日期（格式：YYYY-MM-DD）
     * @return 车次信息列表
     */
    @Tool("查询车次信息，根据出发地、目的地和日期返回可用车次列表")
    public List<Object> searchTrains(
            @P(value = "出发地", required = true) String from,
            @P(value = "目的地", required = true) String to,
            @P(value = "出发日期", required = true) String date) {
        log.warn("AIBusinessTool.searchTrains 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.searchTrains 未实现，请在具体微服务中实现");
    }

    /**
     * 获取车次详情
     * @param trainNo 车次号（如 "G1234"）
     * @return 车次详情
     */
    @Tool("获取车次详情，根据车次号返回详细信息。注意：参数必须是车次号字符串（如 'G1234'），不是数据库ID。")
    public Object getTrainDetail(@P(value = "车次号", required = true) String trainNo) {
        log.warn("AIBusinessTool.getTrainDetail 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.getTrainDetail 未实现，请在具体微服务中实现");
    }

    /**
     * 创建订单
     * @param trainId 车次ID
     * @param trainDate 乘车日期
     * @param startStation 出发站
     * @param endStation 到达站
     * @param seatType 座位类型
     * @param passengerNames 乘客姓名列表（逗号分隔）
     * @param idCards 身份证号列表（逗号分隔）
     * @return 创建的订单
     */
    @Tool("购买车票，根据车次信息、乘客信息和座位类型下单")
    public Object createOrder(
            @P(value = "车次ID", required = true) Long trainId,
            @P(value = "出发日期", required = true) String trainDate,
            @P(value = "出发站", required = true) String startStation,
            @P(value = "到达站", required = true) String endStation,
            @P(value = "座位类型", required = true) Integer seatType,
            @P(value = "乘客姓名", required = true) String passengerNames,
            @P(value = "身份证号", required = true) String idCards) {
        log.warn("AIBusinessTool.createOrder 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.createOrder 未实现，请在具体微服务中实现");
    }

    /**
     * 支付订单
     * @param orderNo 订单号
     * @return 支付结果
     */
    @Tool("支付订单，根据订单号完成支付")
    public boolean payOrder(@P(value = "订单号", required = true) String orderNo) {
        log.warn("AIBusinessTool.payOrder 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.payOrder 未实现，请在具体微服务中实现");
    }

    /**
     * 退票
     * @param orderNo 订单号
     * @return 退票结果
     */
    @Tool("取消订单，根据订单号取消未支付的订单或退票")
    public boolean refundOrder(@P(value = "订单号", required = true) String orderNo) {
        log.warn("AIBusinessTool.refundOrder 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.refundOrder 未实现，请在具体微服务中实现");
    }

    /**
     * 获取用户订单列表
     * @return 订单列表
     */
    @Tool("查询用户的所有订单")
    public List<Object> getUserOrders() {
        log.warn("AIBusinessTool.getUserOrders 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.getUserOrders 未实现，请在具体微服务中实现");
    }

    /**
     * 获取订单详情
     * @param orderNo 订单号
     * @return 订单详情
     */
    @Tool("查询订单详情，根据订单号返回订单详细信息")
    public Object getOrderDetail(
            @P(value = "订单号", required = true) String orderNo) {
        log.warn("AIBusinessTool.getOrderDetail 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.getOrderDetail 未实现，请在具体微服务中实现");
    }

    /**
     * 更新个人信息
     * @param realName 真实姓名
     * @param idCard 身份证号
     * @return 更新结果
     */
    @Tool("更新个人信息，包括真实姓名和身份证号")
    public boolean updateProfile(
            @P(value = "真实姓名", required = true) String realName,
            @P(value = "身份证号", required = true) String idCard) {
        log.warn("AIBusinessTool.updateProfile 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.updateProfile 未实现，请在具体微服务中实现");
    }

    /**
     * 获取常用联系人列表
     * @return 联系人列表
     */
    @Tool("获取常用联系人列表")
    public List<Object> getPassengers() {
        log.warn("AIBusinessTool.getPassengers 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.getPassengers 未实现，请在具体微服务中实现");
    }

    /**
     * 添加常用联系人
     * @param name 姓名
     * @param idCard 身份证号
     * @param phone 手机号
     * @return 添加的联系人
     */
    @Tool("添加常用联系人")
    public Object addPassenger(
            @P(value = "姓名", required = true) String name,
            @P(value = "身份证号", required = true) String idCard,
            @P(value = "手机号", required = true) String phone) {
        log.warn("AIBusinessTool.addPassenger 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.addPassenger 未实现，请在具体微服务中实现");
    }

    /**
     * 删除常用联系人
     * @param passengerId 联系人ID
     * @return 删除结果
     */
    @Tool("删除常用联系人")
    public boolean deletePassenger(@P(value = "联系人ID", required = true) Long passengerId) {
        log.warn("AIBusinessTool.deletePassenger 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.deletePassenger 未实现，请在具体微服务中实现");
    }

    /**
     * 获取当前用户个人信息
     * @return 用户个人信息（包含真实姓名和身份证号）
     */
    @Tool("获取当前用户个人信息")
    public Map<String, String> getUserProfile() {
        log.warn("AIBusinessTool.getUserProfile 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.getUserProfile 未实现，请在具体微服务中实现");
    }

    /**
     * 转接人工客服，将当前会话转接给人工客服，可提供转接原因
     * @param reason 转接原因（可选）
     * @return 转接结果消息
     */
    @Tool("转接人工客服，将当前会话转接给人工客服，可提供转接原因,")
    public String transferToHuman(@P(value = "转接原因", required = false) String reason) {
        log.warn("AIBusinessTool.transferToHuman 未实现，请在具体微服务中实现");
        throw new RuntimeException("AIBusinessTool.transferToHuman 未实现，请在具体微服务中实现");
    }
}