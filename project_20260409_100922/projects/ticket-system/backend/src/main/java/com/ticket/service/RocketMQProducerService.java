package com.ticket.service;

import com.ticket.dto.mq.*;
import com.ticket.enums.MQTopics;
import com.ticket.util.MQIdempotentUtil;
import jakarta.annotation.Resource;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * 统一RocketMQ消息生产者服务
 * 封装所有Topic的消息发送逻辑，提供类型安全的发送方法
 */
@Service
public class RocketMQProducerService {

    private static final Logger logger = LoggerFactory.getLogger(RocketMQProducerService.class);

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    // ==================== P0: 订单相关 ====================

    /**
     * 发送订单排队消息（削峰核心：入队后Consumer异步执行DB写入）
     * 使用同步发送确保消息可靠到达
     */
    public void sendOrderQueueMessage(OrderQueueRequest request) {
        if (request == null || request.getRequestId() == null) {
            logger.error("订单排队请求为空，无法发送MQ");
            return;
        }
        // 使用requestId作为message key，保证同一用户的消息有序
        boolean success = syncSend(MQTopics.ORDER_QUEUE, request.getRequestId(), request);
        if (!success) {
            logger.error("订单排队消息发送失败: requestId={}", request.getRequestId());
            throw new RuntimeException("下单请求入队失败，请重试");
        }
        logger.info("订单排队消息已发送: requestId={}, userId={}",
                request.getRequestId(), request.getUserId());
    }

    /**
     * 发送订单创建事件
     */
    public void sendOrderCreatedEvent(OrderCreatedEvent event) {
        sendMessage(MQTopics.TICKET_ORDER, event.getOrderNo(), event);
    }

    /**
     * 发送支付确认事件
     */
    public void sendPaymentConfirmedEvent(PaymentConfirmedEvent event) {
        sendMessage(MQTopics.TICKET_PAYMENT, event.getOrderNo(), event);
    }

    // ==================== P1: 聊天/日志 ====================

    /**
     * 发送聊天记录事件（异步持久化）
     */
    public void sendChatRecordEvent(ChatRecordEvent event) {
        sendMessage(MQTopics.AI_CHAT_TRACE,
                event.getUserId() + ":" + System.currentTimeMillis(), event);
    }

    /**
     * 发送操作日志事件（异步写入DB）
     */
    public void sendOperationLogEvent(OperationLogEvent event) {
        sendMessage(MQTopics.OPERATION_LOG,
                event.getOperation() + ":" + System.currentTimeMillis(), event);
    }

    // ==================== P2: 知识库 ====================

    /**
     * 发送知识库同步事件
     */
    public void sendKnowledgeSyncEvent(KnowledgeSyncEvent event) {
        sendMessage(MQTopics.KNOWLEDGE_SYNC, "knowledge-sync", event);
    }

    // ==================== 内部方法 ====================

    /**
     * 异步发送消息（不阻塞业务线程）
     */
    private void sendMessage(String topic, String key, Object message) {
        try {
            Message<Object> msg = MessageBuilder.withPayload(message)
                    .setHeader("KEYS", key)
                    .build();
            rocketMQTemplate.asyncSend(topic, msg, new SendCallback() {
                @Override
                public void onSuccess(SendResult result) {
                    logger.debug("RocketMQ消息发送成功: topic={}, msgId={}",
                            topic, result.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    logger.error("RocketMQ消息发送失败: topic={}, key={}", topic, key, e);
                }
            });
        } catch (Exception e) {
            logger.error("RocketMQ消息发送异常: topic={}, key={}", topic, key, e);
        }
    }

    /**
     * 同步发送（用于关键业务场景需要确认发送成功的场景）
     */
    public boolean syncSend(String topic, String key, Object message) {
        try {
            Message<Object> msg = MessageBuilder.withPayload(message)
                    .setHeader("KEYS", key)
                    .build();
            SendResult result = rocketMQTemplate.syncSend(topic, msg);
            logger.info("RocketMQ同步发送成功: topic={}, msgId={}", topic, result.getMsgId());
            return true;
        } catch (Exception e) {
            logger.error("RocketMQ同步发送失败: topic={}, key={}", topic, key, e);
            return false;
        }
    }
}
