package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单实体
 */
@Data
@TableName("`order`")
public class Order {

    /**
     * 订单ID
     */
    @TableId(type = IdType.ASSIGN_ID)
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
     * 首段线段 ID / 兼容直筒；业务真源见 order_route_leg
     */
    private Long trainId;

    /**
     * 首段车次号，展示用
     */
    private String trainNo;

    /**
     * SKU：线段 id 按行程顺序用 '-' 拼接
     */
    private String routeSku;

    /**
     * {@link com.ticket.enums.RouteType}
     */
    private String routeType;

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

    /**
     * 订单明细列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<OrderItem> items;

    /**
     * 用户手机号（非数据库字段，关联查询用）
     */
    @TableField(exist = false)
    private String userPhone;

    /** 行程段（查询时填充） */
    @TableField(exist = false)
    private List<OrderRouteLeg> legs;
}