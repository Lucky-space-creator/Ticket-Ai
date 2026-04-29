package com.ticket.train.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.common.entity.Train;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车次 Mapper
 */
@Mapper
public interface TrainMapper extends BaseMapper<Train> {
}