package com.ticket.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单/MQ 使用的行程段（线段 id + OD 快照）。
 */
@Data
@NoArgsConstructor
public class RouteLeg implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 线段表主键 */
    private Long segmentId;

    private String trainNo;

    private String fromStation;

    private String toStation;

    /** 该段票价快照（下单校验用） */
    private BigDecimal segmentPrice;

    private LocalDateTime plannedDepartAt;

    private LocalDateTime plannedArriveAt;
}
