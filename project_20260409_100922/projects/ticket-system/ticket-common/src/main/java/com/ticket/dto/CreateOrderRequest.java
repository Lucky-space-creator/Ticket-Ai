package com.ticket.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建订单请求
 */
@Data
public class CreateOrderRequest {

    /**
     * 车次ID
     */
    private Long trainId;

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