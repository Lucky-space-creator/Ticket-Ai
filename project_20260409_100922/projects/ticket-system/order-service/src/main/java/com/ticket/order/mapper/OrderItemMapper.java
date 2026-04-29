package com.ticket.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细 Mapper
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}