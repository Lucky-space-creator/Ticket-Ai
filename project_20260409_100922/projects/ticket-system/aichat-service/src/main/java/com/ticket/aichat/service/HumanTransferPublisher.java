package com.ticket.aichat.service;

import com.ticket.dto.mq.ChatRecordEvent;
import com.ticket.enums.BusinessStatus;
import com.ticket.service.RocketMQProducerService;
import com.ticket.util.MQIdempotentUtil;
import com.ticket.util.TraceContext;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 将「转人工」需求写入与 {@link com.ticket.aichat.service.impl.AIChatServiceImpl} 相同的消息通道，供坐席侧消费。
 */
@Service
public class HumanTransferPublisher {

    private static final Logger log = LoggerFactory.getLogger(HumanTransferPublisher.class);

    @Resource
    private RocketMQProducerService rocketMQProducerService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    /**
     * @param userId    当前用户
     * @param sessionId 会话 ID（一般与 AI 消息所在会话一致）
     * @param reason    可选说明（工具调用时可填）
     */
    public String publish(Long userId, String sessionId, String reason) {
        if (userId == null) {
            log.warn("用户未登录，无法转人工");
            return "请先登录后再请求人工客服";
        }
        if (sessionId == null) {
            return "会话异常，请刷新页面后重试";
        }
        try {
            ChatRecordEvent event = new ChatRecordEvent();
            event.setMessageId(idempotentUtil.generateMessageId());
            event.setUserId(userId);
            event.setSessionId(sessionId);
            String msg = "用户请求转人工客服";
            if (reason != null && !reason.isBlank()) {
                msg = msg + "，说明：" + reason.trim();
            }
            event.setMessage(msg);
            event.setMsgType(BusinessStatus.MSG_TYPE_PENDING);
            event.setConfidence(java.math.BigDecimal.ONE);
            event.setInputTokens(0);
            event.setOutputTokens(0);
            event.setCreatedAt(LocalDateTime.now());
            event.setTraceId(TraceContext.getTraceId());
            rocketMQProducerService.sendChatRecordEvent(event);
            log.info("用户 {} 触发转人工，sessionId={}", userId, sessionId);
            return "已为您转接人工客服，请稍候，客服人员将很快为您服务。";
        } catch (Exception e) {
            log.error("触发转人工服务失败", e);
            return "转人工服务暂时不可用，请稍后再试或直接联系客服。";
        }
    }
}
