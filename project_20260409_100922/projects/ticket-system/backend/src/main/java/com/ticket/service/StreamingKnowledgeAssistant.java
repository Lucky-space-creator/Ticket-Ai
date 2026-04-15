package com.ticket.service;

import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * 流式智能客服 AI 服务接口
 * LangChain4j 会自动生成实现类，支持流式输出
 */
public interface StreamingKnowledgeAssistant {

    /**
     * 流式聊天接口
     * @param question 用户问题
     * @return 流式 AI 响应（逐个token）
     */
    @UserMessage("{{question}}")
    Flux<String> chat(String question);
}