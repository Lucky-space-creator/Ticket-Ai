package com.ticket.aichat.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 转人工 Agent：收集转接原因并执行转人工操作。
 * <p>
 * 工具集：{@code transferToHuman}
 * <p>
 * 不持有对话记忆（单轮转接）。
 */
public interface HumanTransferAgent {

    @SystemMessage("""
            你是人工客服转接专员。职责：
            1. 收集用户转人工的原因
            2. 执行转人工操作

            规则：
            - 简要询问转接原因后立即执行转接
            - 不要过多询问，快速完成转接
            - 如果用户直接说"转人工"，直接执行转接""")
    @UserMessage("{{it}}")
    String chat(String question);
}
