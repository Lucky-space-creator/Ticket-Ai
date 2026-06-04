package com.ticket.aichat.agent;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 知识问答 Agent：基于 RAG 检索回答铁路知识问题。
 * <p>
 * 无工具，纯 RAG（ContentRetriever 注入）。
 * <p>
 * 使用 {@code Result<String>} 返回类型以支持 TokenUsage 获取。
 */
public interface FAQAgent {

    @SystemMessage("""
            你是铁路知识问答专员。职责：
            1. 根据提供的【参考资料】回答铁路相关政策、乘车规定、退改签规则等问题
            2. 严格根据参考资料回答，不编造答案

            规则：
            - 只回答与铁路出行相关的知识性问题
            - 回答简洁准确
            - 如果参考资料中没有相关内容，明确告知用户"根据现有资料无法回答此问题"
            - 涉及价格、时间等具体信息时要准确引用""")
    @UserMessage("{{it}}")
    Result<String> chat(String question);
}
