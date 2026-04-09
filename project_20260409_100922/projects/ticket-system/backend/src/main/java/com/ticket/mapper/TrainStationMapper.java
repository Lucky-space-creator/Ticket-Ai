package com.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.entity.TrainStation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车次停靠站 Mapper
 */
@Mapper
public interface TrainStationMapper extends BaseMapper<TrainStation> {
}
