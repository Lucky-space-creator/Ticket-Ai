package com.ticket.aichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客服会话 Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}