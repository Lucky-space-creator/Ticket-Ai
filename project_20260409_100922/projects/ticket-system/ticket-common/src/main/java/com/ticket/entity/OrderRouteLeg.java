package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_route_leg")
public class OrderRouteLeg {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Integer legSeq;

    private Long segmentTrainId;

    private String trainNo;

    private String fromStation;

    private String toStation;

    private BigDecimal segmentPrice;

    private LocalDateTime plannedDepartAt;

    private LocalDateTime plannedArriveAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
