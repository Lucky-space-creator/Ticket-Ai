package com.ticket.aichat.tool;

import com.ticket.aichat.service.ChatSessionService;
import com.ticket.aichat.service.HumanTransferPublisher;
import com.ticket.util.UserContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 转人工工具：将对话转接给人工客服。
 * <p>
 * 供 HumanTransferAgent 使用。
 */
@Component
public class HumanTransferTools {

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private HumanTransferPublisher humanTransferPublisher;

    @Tool("转接人工客服，将当前会话转接给人工客服，可提供转接原因")
    public String transferToHuman(@P(value = "转接原因", required = false) String reason) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return "请先登录后再请求人工客服";
        }
        String sessionId = chatSessionService.getOrCreateAiOnlySessionId(userId);
        return humanTransferPublisher.publish(userId, sessionId, reason);
    }
}
