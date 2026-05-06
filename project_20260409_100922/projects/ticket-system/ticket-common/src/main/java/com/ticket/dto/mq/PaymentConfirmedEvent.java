package com.ticket.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import com.ticket.dto.internal.TrainStockCommand;

import java.time.LocalDate;
import java.util.List;

/**
 * 支付确认事件消息
 * 支付成功后发送，触发库存确认等后续操作
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentConfirmedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息唯一ID */
    private String messageId;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 兼容旧单（无 legs） */
    @Deprecated
    private Long trainId;

    /** 与各段 Redis locked key 对齐；若非空则由消费者单笔 confirm batch */
    private List<TrainStockCommand> stockLegs;

    /** 乘车日期 */
    private LocalDate trainDate;

    /** 出发站 */
    private String startStation;

    /** 到达站 */
    private String endStation;

    /** 席别类型 */
    private Integer seatType;

    /** 确认数量（订单明细数） */
    private int count;

    /** 支付时间戳 */
    private long payTimestamp;
}
