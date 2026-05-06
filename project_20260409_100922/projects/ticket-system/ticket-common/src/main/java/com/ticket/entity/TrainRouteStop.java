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

    /** 车次编号 */
    private String trainNo;

    /** 站名 */
    private String stationName;

    /** 站序 */
    private Integer stationNo;

    /** 到站时间 */
    private LocalTime arriveTime;

    /** 出发时间 */
    private LocalTime departTime;
}
