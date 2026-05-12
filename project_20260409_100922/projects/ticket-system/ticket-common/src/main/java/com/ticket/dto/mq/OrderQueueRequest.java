package com.ticket.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import com.ticket.dto.RouteLeg;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单排队请求（MQ入队消息体）
 * 包含创建订单所需的全部信息，由Consumer异步消费后执行DB写入
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderQueueRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 唯一请求ID（幂等键，用于防重复和轮询查询） */
    private String requestId;

    /** 用户ID */
    private Long userId;

    /**
     * 兼容直筒：等价于首节 segmentId；业务真源为 {@link #legs}。
     */
    @Deprecated
    private Long trainId;

    /** 线路 SKU，与 legs 同源 */
    private String routeSku;

    /** SINGLE / DIRECT / TRANSFER */
    private String routeType;

    /** 服务端校验通过的行程（含线段 id）；非空时使用批量 Lua 预扣 */
    private List<RouteLeg> legs;

    /** 乘车日期 */
    private String trainDate;

    /** 出发站 */
    private String startStation;

    /** 到达站 */
    private String endStation;

    /** 席别类型 */
    private Integer seatType;

    /** 乘客列表 */
    private List<PassengerItem> items;

    /** 入队时间戳 */
    private Long enqueueTime;

    /** 客户端IP（用于日志追踪） */
    private String clientIp;

    /**
     * 乘客信息项
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PassengerItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        /** 乘客姓名 */
        private String passengerName;
        /** 身份证号（已加密） */
        private String idCard;
        /** 票价 */
        private BigDecimal price;
    }
}
