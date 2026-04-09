package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
@TableName("`order`")
public class Order {

    /**
     * 订单ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 车次ID
     */
    private Long trainId;

    /**
     * 车次号
     */
    private String trainNo;

    /**
     * 乘车日期
     */
    private LocalDate trainDate;

    /**
     * 出发站
     */
    private String startStation;

    /**
     * 到达站
     */
    private String endStation;

    /**
     * 发车时间
     */
    private LocalDateTime departTime;

    /**
     * 席别
     */
    private Integer seatType;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 状态 0-待支付 1-已支付 2-已退票 3-已取消
     */
    private Integer status;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
