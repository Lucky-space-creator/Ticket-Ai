package com.ticket.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单创建事件消息
 * 订单创建成功后发送，触发后续异步处理
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreatedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息唯一ID（用于幂等性校验） */
    private String messageId;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 车次ID */
    private Long trainId;

    /** 车次号 */
    private String trainNo;

    /** 乘车日期 */
    private LocalDate trainDate;

    /** 出发站 */
    private String startStation;

    /** 到达站 */
    private String endStation;

    /** 席别类型 */
    private Integer seatType;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 线路 SKU，便于观测与缓存 */
    private String routeSku;

    /** 购票数量（订单明细数） */
    private Integer itemCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
