package com.ticket.aichat.consumer;

import com.ticket.aichat.service.KnowledgeBaseService;
import com.ticket.dto.mq.KnowledgeSyncEvent;
import com.ticket.enums.MQTopics;
import com.ticket.util.MQIdempotentUtil;
import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 知识库变更后同步向量库（与 backend 同组名，勿与 backend 同时订阅本 Topic）。
 */
@Component
@RocketMQMessageListener(
        topic = MQTopics.KNOWLEDGE_SYNC,
        consumerGroup = "knowledge-sync-consumer-group",
        messageModel = MessageModel.CLUSTERING
)
public class KnowledgeSyncMqConsumer implements RocketMQListener<KnowledgeSyncEvent> {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncMqConsumer.class);

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    public void onMessage(KnowledgeSyncEvent event) {
        if (event == null || event.getMessageId() == null) {
            log.warn("收到空知识库同步事件，跳过");
            return;
        }
        if (idempotentUtil.isConsumed(MQTopics.KNOWLEDGE_SYNC, event.getMessageId())) {
            return;
        }

        log.info("开始消费知识库同步事件: type={}, source={}",
                event.getSyncType(), event.getTriggerSource());

        long startTime = System.currentTimeMillis();
        try {
            knowledgeBaseService.syncToVectorStore();
            log.info("知识库同步完成，耗时: {}ms, source={}",
                    System.currentTimeMillis() - startTime, event.getTriggerSource());
        } catch (Exception e) {
            log.error("知识库同步失败(将重试): source={}, error={}",
                    event.getTriggerSource(), e.getMessage());
            throw e;
        }
    }
}
