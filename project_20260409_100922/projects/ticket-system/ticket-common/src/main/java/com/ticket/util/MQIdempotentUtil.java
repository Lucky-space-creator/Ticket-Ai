package com.ticket.util;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ 消费幂等（Redis）
 * <p>
 * 正确用法：先 {@link #alreadyConsumed}，业务处理成功后 {@link #markConsumed}。
 * 禁止在业务开始前写入终态键（否则进程崩溃会导致重投被跳过、丢单）。
 */
@Slf4j
@Component
public class MQIdempotentUtil {

    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:";

    private static final String CONSUMED_MARK = "1";

    private static final long DEFAULT_TTL_HOURS = 24;

    @Resource
    private RedisUtil redisUtil;

    /**
     * 是否已成功消费过（只读，不改变 Redis）
     */
    public boolean alreadyConsumed(String topic, String messageId) {
        if (topic == null || messageId == null || messageId.isEmpty()) {
            return false;
        }
        String key = buildKey(topic, messageId);
        return Boolean.TRUE.equals(redisUtil.exists(key));
    }

    /**
     * 标记消息已成功处理（应在业务提交成功后调用）
     */
    public void markConsumed(String topic, String messageId) {
        if (topic == null || messageId == null || messageId.isEmpty()) {
            return;
        }
        String key = buildKey(topic, messageId);
        redisUtil.set(key, CONSUMED_MARK, DEFAULT_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * @deprecated 语义为先占位的乐观“防重”，会导致崩溃后重投被误跳过。请改用 {@link #alreadyConsumed} +
     *             {@link #markConsumed}。
     */
    @Deprecated(forRemoval = false)
    public boolean isConsumed(String topic, String messageId) {
        return alreadyConsumed(topic, messageId);
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
