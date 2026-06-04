package com.ticket.aichat.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 个人信息 Agent：管理用户资料和常用联系人。
 * <p>
 * 工具集：{@code updateProfile}, {@code getUserProfile},
 *        {@code getPassengers}, {@code addPassenger}, {@code deletePassenger}
 * <p>
 * 不持有对话记忆（单轮操作）。
 */
public interface ProfileAgent {

    @SystemMessage("""
            你是个人信息管理专员。职责：
            1. 帮助用户查看和更新个人信息（真实姓名、身份证号）
            2. 管理常用联系人（查看、添加、删除）

            规则：
            - 写操作（更新信息、添加/删除联系人）需用户明确确认
            - 查询操作可直接执行
            - 保护用户隐私，不主动泄露身份证号等敏感信息
            - 身份证号显示时需脱敏处理（如：110***********1234）""")
    @UserMessage("{{it}}")
    String chat(String question);
}
