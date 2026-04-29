package com.ticket.consumer;

import com.ticket.dto.mq.KnowledgeSyncEvent;
import com.ticket.enums.MQTopics;
import com.ticket.service.KnowledgeBaseService;
import com.ticket.util.MQIdempotentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 知识库同步消费者
 * 异步执行向量库同步（Embedding向量化），耗时5~30秒
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopics.KNOWLEDGE_SYNC,
        consumerGroup = "knowledge-sync-consumer-group"
)
public class KnowledgeSyncConsumer implements RocketMQListener<KnowledgeSyncEvent> {

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

        // 幂等性校验：短时间内多次增删操作只需执行一次全量同步
        if (idempotentUtil.isConsumed(MQTopics.KNOWLEDGE_SYNC, event.getMessageId())) {
            return;
        }

        log.info("开始消费知识库同步事件: type={}, source={}",
                event.getSyncType(), event.getTriggerSource());

        long startTime = System.currentTimeMillis();

        // 调用同步服务（全量同步：清空→向量化→入库）
        knowledgeBaseService.syncToVectorStore();

        long duration = System.currentTimeMillis() - startTime;
        log.info("知识库同步完成，耗时: {}ms, source={}", duration, event.getTriggerSource());
    }
}
