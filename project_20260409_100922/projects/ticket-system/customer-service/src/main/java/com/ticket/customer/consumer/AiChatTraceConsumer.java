package com.ticket.customer.consumer;

import com.alibaba.fastjson2.JSON;
import com.ticket.customer.handler.ChatWebSocketHandler;
import com.ticket.customer.mapper.ChatRecordMapper;
import com.ticket.dto.mq.ChatRecordEvent;
import com.ticket.entity.ChatRecord;
import com.ticket.enums.MQTopics;
import com.ticket.util.MQIdempotentUtil;
import com.ticket.util.TraceMdcHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 聊天轨迹：落库 + WebSocket 广播（与 backend 同组名，勿与 backend 同时订阅本 Topic）。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopics.AI_CHAT_TRACE,
        consumerGroup = "chat-record-consumer-group",
        messageModel = MessageModel.CLUSTERING
)
public class AiChatTraceConsumer implements RocketMQListener<ChatRecordEvent> {

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    public void onMessage(ChatRecordEvent event) {
        try {
            if (event == null || event.getMessageId() == null) {
                log.warn("收到空聊天记录事件，跳过");
                return;
            }
            if (idempotentUtil.alreadyConsumed(MQTopics.AI_CHAT_TRACE, event.getMessageId())) {
                return;
            }

            ChatRecord chatRecord = new ChatRecord();
            chatRecord.setUserId(event.getUserId());
            chatRecord.setSessionId(event.getSessionId());
            chatRecord.setMessage(event.getMessage());
            chatRecord.setMsgType(event.getMsgType());
            chatRecord.setEmployeeId(event.getEmployeeId());
            chatRecord.setIsRead(0);
            chatRecord.setConfidence(event.getConfidence());
            chatRecord.setInputTokens(event.getInputTokens());
            chatRecord.setOutputTokens(event.getOutputTokens());
            chatRecord.setCreatedAt(
                    event.getCreatedAt() != null ? event.getCreatedAt() : LocalDateTime.now()
            );
            chatRecordMapper.insert(chatRecord);
            idempotentUtil.markConsumed(MQTopics.AI_CHAT_TRACE, event.getMessageId());

            broadcastToWebSocket(event);
            log.debug("聊天记录持久化完成: userId={}, msgType={}",
                    event.getUserId(), event.getMsgType());
        } catch (Exception e) {
            log.error("处理聊天记录事件失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void broadcastToWebSocket(ChatRecordEvent event) {
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", event.getMessage());
            wsMessage.put("msgType", event.getMsgType());
            wsMessage.put("traceId", event.getTraceId());
            wsMessage.put("userId", event.getUserId());
            wsMessage.put("timestamp", System.currentTimeMillis());
            if (event.getEmployeeId() != null) {
                wsMessage.put("employeeId", event.getEmployeeId());
            }

            TraceMdcHelper.runWithTraceId(event.getTraceId(), () ->
                    chatWebSocketHandler.sendMessageToSession(
                            event.getSessionId(),
                            JSON.toJSONString(wsMessage)
                    ));
        } catch (Exception e) {
            log.warn("WebSocket广播失败，不影响记录保存: sessionId={}", event.getSessionId(), e);
        }
    }
}
