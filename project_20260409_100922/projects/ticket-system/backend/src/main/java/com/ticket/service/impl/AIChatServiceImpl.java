package com.ticket.service.impl;

import com.ticket.service.AIChatService;
import com.ticket.service.KnowledgeAssistant;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 智能客服服务实现
 * 负责处理AI聊天业务逻辑，调用底层AI服务
 */
@Service
public class AIChatServiceImpl implements AIChatService {

    @Resource
    private KnowledgeAssistant knowledgeAssistant;

    @Override
    public String chat(String question) {
        // 可以在这里添加业务逻辑，如日志记录、输入验证、缓存等
        // 后续微服务化时，可以将这部分逻辑独立为微服务
        return knowledgeAssistant.chat(question);
    }
}
