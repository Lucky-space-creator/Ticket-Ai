package com.ticket.aichat.consumer;

import com.ticket.aichat.service.DocumentIngestionService;
import com.ticket.dto.mq.KnowledgeSyncEvent;
import com.ticket.enums.MQTopics;
import com.ticket.util.MQIdempotentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 手册向量增量对账（FAQ 不向量化）。
 * 与 backend 同组 consumer 时注意勿双重订阅冲突。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MQTopics.KNOWLEDGE_SYNC,
        consumerGroup = "knowledge-sync-consumer-group",
        messageModel = MessageModel.CLUSTERING
)
public class KnowledgeSyncMqConsumer implements RocketMQListener<KnowledgeSyncEvent> {

    @Resource
    private DocumentIngestionService documentIngestionService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    public void onMessage(KnowledgeSyncEvent event) {
        if (event == null || event.getMessageId() == null) {
            log.warn("收到空手册同步事件，跳过");
            return;
        }
        if (idempotentUtil.alreadyConsumed(MQTopics.KNOWLEDGE_SYNC, event.getMessageId())) {
            return;
        }

        log.info("开始消费手册向量对账事件: type={}, source={}",
                event.getSyncType(), event.getTriggerSource());

        long startTime = System.currentTimeMillis();
        try {
            documentIngestionService.reconcileManualDocuments();
            log.info("手册对账完成，耗时: {}ms, source={}",
                    System.currentTimeMillis() - startTime, event.getTriggerSource());
            idempotentUtil.markConsumed(MQTopics.KNOWLEDGE_SYNC, event.getMessageId());
        } catch (Exception e) {
            log.error("手册对账失败(将重试): source={}, error={}",
                    event.getTriggerSource(), e.getMessage());
            throw e;
        }
    }
}
