package com.ticket.aichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.entity.ChatRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客服对话记录 Mapper
 */
@Mapper
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {
}