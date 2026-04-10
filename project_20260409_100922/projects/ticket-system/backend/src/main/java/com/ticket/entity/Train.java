package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 车次实体
 */
@Data
@TableName("train")
public class Train {

    /**
     * 车次ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 车次号 如G1234
     */
    private String trainNo;

    /**
     * 类型 1-高铁 2-动车 3-普快
     */
    private Integer trainType;

    /**
     * 始发站
     */
    private String startStation;

    /**
     * 终到站
     */
    private String endStation;

    /**
     * 发车时间
     */
    private LocalTime startTime;

    /**
     * 到达时间
     */
    private LocalTime endTime;

    /**
     * 状态 0-停运 1-正常
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
