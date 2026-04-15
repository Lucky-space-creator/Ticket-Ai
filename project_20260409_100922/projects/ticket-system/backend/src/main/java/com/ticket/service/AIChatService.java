package com.ticket.service;

import reactor.core.publisher.Flux;

import java.io.FileNotFoundException;

/**
 * 智能客服服务接口
 * 提供AI聊天功能，封装底层AI模型调用和业务逻辑
 */
public interface AIChatService {

    /**
     * 发送问题给AI客服并获取回答
     * @param question 用户问题
     * @return AI回答内容
     */
    String chat(String question);

    /**
     * 流式发送问题给AI客服并获取回答
     * @param question 用户问题
     * @return 流式AI回答内容
     */
    Flux<String> streamingChat(String question);

    /**
     * 获取系统提示词文档
     */
//    String getSystemPrompt() throws FileNotFoundException;
}
