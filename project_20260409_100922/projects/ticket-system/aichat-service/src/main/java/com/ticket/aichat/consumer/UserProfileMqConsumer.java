package com.ticket.aichat.consumer;

import com.ticket.aichat.service.impl.UserProfileServiceImpl;
import com.ticket.dto.mq.UserProfileGenerateEvent;
import com.ticket.enums.MQTopics;
import com.ticket.util.MQIdempotentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 用户画像生成 MQ 消费者
 * 消费画像生成事件，调用 UserProfileServiceImpl 实际执行 LLM 总结和存储
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MQTopics.USER_PROFILE_GENERATE,
        consumerGroup = "user-profile-consumer-group",
        messageModel = MessageModel.CLUSTERING
)
public class UserProfileMqConsumer implements RocketMQListener<UserProfileGenerateEvent> {

    @Resource
    private UserProfileServiceImpl userProfileService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    public void onMessage(UserProfileGenerateEvent event) {
        if (event == null || event.getMessageId() == null) {
            log.warn("收到空画像生成事件，跳过");
            return;
        }

        // 幂等检查
        if (idempotentUtil.alreadyConsumed(MQTopics.USER_PROFILE_GENERATE, event.getMessageId())) {
            log.debug("画像生成事件已消费过，跳过: messageId={}", event.getMessageId());
            return;
        }

        log.info("消费画像生成事件: userId={}, source={}", event.getUserId(), event.getTriggerSource());

        long startTime = System.currentTimeMillis();
        try {
            userProfileService.doGenerateProfile(event.getUserId(), event.getSessionId());
            log.info("画像生成完成，耗时: {}ms, userId={}",
                    System.currentTimeMillis() - startTime, event.getUserId());
            idempotentUtil.markConsumed(MQTopics.USER_PROFILE_GENERATE, event.getMessageId());
        } catch (Exception e) {
            log.error("画像生成失败(将重试): userId={}, error={}", event.getUserId(), e.getMessage());
            throw e;
        }
    }
}
