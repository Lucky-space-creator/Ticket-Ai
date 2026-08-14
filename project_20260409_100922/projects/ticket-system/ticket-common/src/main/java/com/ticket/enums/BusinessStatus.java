package com.ticket.enums;

/**
 * 业务状态常量
 */
public class BusinessStatus {

    /**
     * 用户状态
     */
    public static final Integer USER_STATUS_DISABLED = 0;  // 禁用
    public static final Integer USER_STATUS_NORMAL = 1;    // 正常

    /**
     * 车次类型
     */
    public static final Integer TRAIN_TYPE_GAOTIE = 1;    // 高铁
    public static final Integer TRAIN_TYPE_DONGCHE = 2;   // 动车
    public static final Integer TRAIN_TYPE_PUKUAI = 3;    // 普快

    /**
     * 车次状态
     */
    public static final Integer TRAIN_STATUS_STOPPED = 0;  // 停运
    public static final Integer TRAIN_STATUS_NORMAL = 1;   // 正常

    /**
     * 席别类型
     */
    public static final Integer SEAT_TYPE_BUSINESS = 1;   // 商务座
    public static final Integer SEAT_TYPE_FIRST = 2;      // 一等座
    public static final Integer SEAT_TYPE_SECOND = 3;     // 二等座
    public static final Integer SEAT_TYPE_SOFT_SLEEPER = 4;  // 软卧
    public static final Integer SEAT_TYPE_HARD_SLEEPER = 5;  // 硬卧
    public static final Integer SEAT_TYPE_HARD_SEAT = 6;  // 硬座

    /**
     * 席别名称映射
     */
    public static final String[] SEAT_TYPE_NAMES = {
        "", "商务座", "一等座", "二等座", "软卧", "硬卧", "硬座"
    };

    /**
     * 订单状态
     */
    public static final Integer ORDER_STATUS_PENDING = 0;   // 待支付
    public static final Integer ORDER_STATUS_PAID = 1;      // 已支付
    public static final Integer ORDER_STATUS_REFUNDED = 2;  // 已退款
    public static final Integer ORDER_STATUS_CANCELLED = 3; // 已取消

    /**
     * 订单状态名称
     */
    public static final String[] ORDER_STATUS_NAMES = {
        "待支付", "已支付", "已退款", "已取消"
    };

    /**
     * 消息类型（客服）
     */
    public static final String MSG_TYPE_USER = "user";   // 用户
    public static final String MSG_TYPE_ROBOT = "robot";  // 机器人
    public static final String MSG_TYPE_PENDING = "pending";  // 用户请求人工客服，等待接入
    public static final String MSG_TYPE_ENDED = "ended";  // 会话已结束
    public static final String MSG_TYPE_EMPLOYEE = "employee";  // 人工客服坐席

    /**
     * 知识库状态
     */
    public static final Integer KB_STATUS_DISABLED = 0;  // 禁用
    public static final Integer KB_STATUS_ENABLED = 1;   // 启用

    /**
     * 获取席别名称
     */
    public static String getSeatTypeName(Integer seatType) {
        if (seatType == null || seatType < 1 || seatType >= SEAT_TYPE_NAMES.length) {
            return "未知";
        }
        return SEAT_TYPE_NAMES[seatType];
    }

    /**
     * 获取订单状态名称
     */
    public static String getOrderStatusName(Integer status) {
        if (status == null || status < 0 || status >= ORDER_STATUS_NAMES.length) {
            return "未知";
        }
        return ORDER_STATUS_NAMES[status];
    }
}