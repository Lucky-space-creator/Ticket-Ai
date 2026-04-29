package com.ticket.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码常量
 */
@Getter
@AllArgsConstructor
public enum ResponseCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 客户端错误 4xx
     */
    ERROR(400, "操作失败"),
    UNAUTHORIZED(401, "未授权，请登录"),
    FORBIDDEN(403, "该用户目前无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /**
     * 服务器错误 5xx
     */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    /**
     * 频率限制 429
     */
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),

    /**
     * 业务错误码 1000-1999
     */
    PARAM_ERROR(1001, "参数错误"),
    PARAM_MISSING(1002, "缺少必要参数"),
    PARAM_INVALID(1003, "参数格式不正确"),

    /**
     * 用户相关 2000-2999
     */
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_PASSWORD_ERROR(2002, "密码错误"),
    USER_PHONE_EXIST(2003, "手机号已存在"),
    USER_DISABLED(2004, "账号已被禁用"),
    USER_TOKEN_INVALID(2005, "Token 无效或已过期"),
    USER_INFO_NOT_COMPLETE(2006, "用户信息不完整，请先实名认证"),

    /**
     * 车票相关 3000-3999
     */
    TRAIN_NOT_FOUND(3001, "车次不存在"),
    TRAIN_STOPPED(3002, "该车次已停运"),
    TICKET_SOLD_OUT(3003, "余票不足"),
    TICKET_BOOKED(3004, "该车次该日期已售罄"),
    SEAT_TYPE_INVALID(3005, "席别不存在"),

    /**
     * 订单相关 4000-4999
     */
    ORDER_NOT_FOUND(4001, "订单不存在"),
    ORDER_PAID(4002, "订单已支付，请勿重复支付"),
    ORDER_REFUNDED(4003, "订单已退款"),
    ORDER_CANCELLED(4004, "订单已取消"),
    ORDER_CAN_NOT_REFUND(4005, "该订单不支持退款"),
    ORDER_TIMEOUT(4006, "订单已超时，请重新下单"),

    /**
     * 客服相关 5000-5999
     */
    CHAT_SESSION_INVALID(5001, "会话无效"),
    CHAT_MESSAGE_EMPTY(5002, "消息内容不能为空");

    /**
     * 响应码
     */
    private final Integer code;

    /**
     * 响应消息
     */
    private final String message;
}