package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 车次停靠序模板（与日期无关）
 */
@Data
@TableName("train_route_stop")
public class TrainRouteStop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String trainNo;

    private String stationName;

    private Integer stationNo;

    private LocalTime arriveTime;

    private LocalTime departTime;
}
