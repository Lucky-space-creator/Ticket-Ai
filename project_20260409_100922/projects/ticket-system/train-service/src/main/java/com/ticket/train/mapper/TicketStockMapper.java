package com.ticket.train.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.entity.TicketStock;
import org.apache.ibatis.annotations.Mapper;

/**
 * 余票库存 Mapper
 */
@Mapper
public interface TicketStockMapper extends BaseMapper<TicketStock> {
}