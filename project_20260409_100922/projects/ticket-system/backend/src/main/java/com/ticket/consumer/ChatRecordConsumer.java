package com.ticket.consumer;

import com.alibaba.fastjson2.JSON;
import com.ticket.dto.mq.ChatRecordEvent;
import com.ticket.entity.ChatRecord;
import com.ticket.enums.MQTopics;
import com.ticket.mapper.ChatRecordMapper;
import com.ticket.handler.ChatWebSocketHandler;
import com.ticket.util.MQIdempotentUtil;
import com.ticket.util.TraceMdcHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 聊天记录消费者
 * 异步执行DB写入 + WebSocket广播
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopics.AI_CHAT_TRACE,
        consumerGroup = "chat-record-consumer-group"
)
public class ChatRecordConsumer implements RocketMQListener<ChatRecordEvent> {

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
            // 幂等性校验
            if (idempotentUtil.isConsumed(MQTopics.AI_CHAT_TRACE, event.getMessageId())) {
                return;
            }

            // 1. 写入数据库
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

            // 2. WebSocket 广播消息（恢复trace上下文到消费者线程）
            broadcastToWebSocket(event);

            log.debug("聊天记录持久化完成: userId={}, msgType={}",
                    event.getUserId(), event.getMsgType());
        } catch (Exception e) {
            log.error("处理聊天记录事件失败: {}", e.getMessage(), e);
            throw e; // 抛出异常触发RocketMQ重试
        }
    }

    /**
     * 通过WebSocket广播消息给前端
     */
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

            TraceMdcHelper.runWithTraceId(event.getTraceId(), () -> {
                chatWebSocketHandler.sendMessageToSession(
                        event.getSessionId(),
                        JSON.toJSONString(wsMessage)
                );
            });
        } catch (Exception e) {
            log.warn("WebSocket广播失败，不影响记录保存: sessionId={}", event.getSessionId(), e);
        }
    }
}
