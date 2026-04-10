package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalTime;

/**
 * 车次停靠站实体
 */
@Data
@TableName("train_station")
public class TrainStation {

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
     * 站点名称
     */
    private String stationName;

    /**
     * 站序(第几站)
     */
    private Integer stationNo;

    /**
     * 到达时间
     */
    private LocalTime arriveTime;

    /**
     * 出发时间
     */
    private LocalTime departTime;
}
