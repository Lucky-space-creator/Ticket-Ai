package com.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.entity.Train;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车次 Mapper
 */
@Mapper
public interface TrainMapper extends BaseMapper<Train> {
}
