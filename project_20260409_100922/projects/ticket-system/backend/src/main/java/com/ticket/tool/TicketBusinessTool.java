package com.ticket.tool;

import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.Passenger;
import com.ticket.entity.Train;
import com.ticket.service.OrderService;
import com.ticket.service.PassengerService;
import com.ticket.service.TrainService;
import com.ticket.service.UserService;
import com.ticket.util.CryptoUtil;
import com.ticket.util.UserContext;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 购票业务工具类
 * 将所有可暴露的API整合起来，形成一个业务工具类，可调用具体的业务
 * 每个方法使用 @Tool 注解，可以被AI自动调用
 */
@Component
public class TicketBusinessTool {

    @Resource
    private TrainService trainService;

    @Resource
    private OrderService orderService;

    @Resource
    private UserService userService;

    @Resource
    private PassengerService passengerService;

    /**
     * 查询车次信息
     * @param from 出发城市
     * @param to 到达城市
     * @param date 出发日期（格式：YYYY-MM-DD）
     * @return 车次信息列表
     */
    @Tool("查询车次信息，根据出发地、目的地和日期返回可用车次列表")
    public List<Train> searchTrains(
            String from,
            String to,
            String date) {
        return trainService.searchTrains(from, to, date);
    }

    /**
     * 获取车次详情
     * @param trainId 车次ID
     * @return 车次详情
     */
    @Tool("获取车次详情，根据车次ID返回详细信息")
    public Train getTrainDetail(Long trainId) {
        return trainService.getTrainDetail(trainId);
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
    public Order createOrder(
            Long trainId,
            String trainDate,
            String startStation,
            String endStation,
            Integer seatType,
            String passengerNames,
            String idCards) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        // 解析乘客信息
        String[] names = passengerNames.split(",");
        String[] cards = idCards.split(",");
        if (names.length != cards.length) {
            throw new RuntimeException("乘客姓名和身份证号数量不匹配");
        }
        // 查询真实票价
        BigDecimal seatPrice = trainService.getSeatPrice(trainId, trainDate, startStation, endStation, seatType);
        // 转换订单明细
        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            OrderItem item = new OrderItem();
            item.setPassengerName(names[i].trim());
            item.setIdCard(CryptoUtil.encrypt(cards[i].trim()));
            item.setPrice(seatPrice);
            items.add(item);
        }
        // 调用订单服务创建订单
        return orderService.createOrder(userId, trainId, trainDate, startStation, endStation, seatType, items);
    }

    /**
     * 支付订单
     * @param orderNo 订单号
     * @return 支付结果
     */
    @Tool("支付订单，根据订单号完成支付")
    public boolean payOrder(String orderNo) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return orderService.payOrder(userId, orderNo);
    }

    /**
     * 退票
     * @param orderNo 订单号
     * @return 退票结果
     */
    @Tool("取消订单，根据订单号取消未支付的订单或退票")
    public boolean refundOrder(String orderNo) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return orderService.refundOrder(userId, orderNo);
    }

    /**
     * 获取用户订单列表
     * @return 订单列表
     */
    @Tool("查询用户的所有订单")
    public List<Order> getUserOrders() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return orderService.getUserOrders(userId);
    }

    /**
     * 获取订单详情
     * @param orderNo 订单号
     * @return 订单详情
     */
    @Tool("查询订单详情，根据订单号返回订单详细信息")
    public Order getOrderDetail(String orderNo) {
        return orderService.getOrderDetail(orderNo);
    }

    /**
     * 更新个人信息
     * @param realName 真实姓名
     * @param idCard 身份证号
     * @return 更新结果
     */
    @Tool("更新个人信息，包括真实姓名和身份证号")
    public boolean updateProfile(
            String realName,
            String idCard) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return userService.updateProfile(userId, realName, idCard);
    }

    /**
     * 获取常用联系人列表
     * @return 联系人列表
     */
    @Tool("获取常用联系人列表")
    public List<Passenger> getPassengers() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return passengerService.getByUserId(userId);
    }

    /**
     * 添加常用联系人
     * @param name 姓名
     * @param idCard 身份证号
     * @param phone 手机号
     * @return 添加的联系人
     */
    @Tool("添加常用联系人")
    public Passenger addPassenger(
            String name,
            String idCard,
            String phone) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return passengerService.addPassenger(userId, name, idCard, phone);
    }

    /**
     * 删除常用联系人
     * @param passengerId 联系人ID
     * @return 删除结果
     */
    @Tool("删除常用联系人")
    public boolean deletePassenger(Long passengerId) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        return passengerService.deletePassenger(passengerId, userId);
    }
}