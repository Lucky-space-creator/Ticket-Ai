package com.ticket.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建订单请求
 */
@Data
public class CreateOrderRequest {

    /**
     * 直筒单段时指定线段 id（与 SKU 二选一）。
     */
    private Long trainId;

    /** 服务端校验下发的 SKU；联程必填 */
    private String routeSku;

    /** DIRECT / TRANSFER / SINGLE，与 searchRoutes 一致 */
    private String routeType;

    /** 多段行程；非空时以各段线段 id 预扣 */
    private List<RouteLeg> legs;

    /**
     * 乘车日期
     */
    private String trainDate;

    /**
     * 出发站
     */
    private String startStation;

    /**
     * 到达站
     */
    private String endStation;

    /**
     * 席别
     */
    private Integer seatType;

    /**
     * 乘客列表
     */
    private List<OrderItemRequest> items;

    @lombok.Data
    public static class OrderItemRequest {
        /**
         * 乘客姓名
         */
        private String passengerName;

        /**
         * 身份证号
         */
        private String idCard;
    }
}