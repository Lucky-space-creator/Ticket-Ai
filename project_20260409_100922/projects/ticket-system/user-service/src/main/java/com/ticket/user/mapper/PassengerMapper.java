package com.ticket.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.common.entity.Passenger;
import org.apache.ibatis.annotations.Mapper;

/**
 * 常用联系人 Mapper
 */
@Mapper
public interface PassengerMapper extends BaseMapper<Passenger> {
}