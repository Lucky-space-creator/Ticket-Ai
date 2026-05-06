package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 余票库存实体
 */
@Data
@TableName("ticket_stock")
public class TicketStock {

    /**
     * ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 车次ID
     */
    private Long trainId;

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
     * 席别 1-商务 2-一等 3-二等 4-软卧 5-硬卧 6-硬座
     */
    private Integer seatType;

    /**
     * 票价
     */
    private BigDecimal price;

    /**
     * 总座位数
     */
    private Integer totalSeats;

    /**
     * 剩余座位数
     */
    private Integer availableSeats;

    /**
     * 是否开售（停售≠预扣）
     */
    private Integer saleEnabled;

    /**
     * 版本号（乐观锁）
     */
    @Version
    private Integer version;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}