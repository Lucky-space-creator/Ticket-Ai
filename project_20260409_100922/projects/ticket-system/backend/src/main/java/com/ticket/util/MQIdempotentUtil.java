package com.ticket.util;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ 幂等性校验工具类
 * 基于Redis实现消息去重，防止重复消费
 */
@Component
public class MQIdempotentUtil {

    private static final Logger logger = LoggerFactory.getLogger(MQIdempotentUtil.class);

    /** 幂等键前缀 */
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:";

    /** 默认过期时间24小时 */
    private static final long DEFAULT_TTL_HOURS = 24;

    @Resource
    private RedisUtil redisUtil;

    /**
     * 检查消息是否已消费（幂等性判断）
     * @param topic Topic名称
     * @param messageId 消息唯一ID
     * @return true=已消费过(应跳过), false=未消费过(标记为已消费)
     */
    public boolean isConsumed(String topic, String messageId) {
        String key = buildKey(topic, messageId);
        Boolean exists = redisUtil.exists(key);
        if (Boolean.TRUE.equals(exists)) {
            logger.debug("消息重复消费，跳过: topic={}, messageId={}", topic, messageId);
            return true;
        }
        // 标记为已消费（setIfAbsent保证原子性）
        redisUtil.set(key, "1", (int) DEFAULT_TTL_HOURS, TimeUnit.HOURS);
        return false;
    }

    /**
     * 生成唯一消息ID
     */
    public String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String buildKey(String topic, String messageId) {
        return IDEMPOTENT_KEY_PREFIX + topic + ":" + messageId;
    }
}
