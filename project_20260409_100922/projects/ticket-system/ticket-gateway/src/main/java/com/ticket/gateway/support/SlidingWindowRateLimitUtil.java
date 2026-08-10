package com.ticket.gateway.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 基于 Redis ZSET 的滑动窗口限流工具类。
 * <p>
 * 通过 Lua 脚本保证「清理过期成员 + 计数 + 写入」的原子性，避免并发下计数超发。
 * Redis 不可用时自动降级放行，保证网关可用性优先。
 */
@Slf4j
public class SlidingWindowRateLimitUtil {

    /**
     * 滑动窗口计数 Lua 脚本。
     * KEYS[1] = 限流 key
     * ARGV[1] = 窗口大小（秒）
     * ARGV[2] = 窗口内最大请求数
     * ARGV[3] = 当前时间戳（毫秒）
     * ARGV[4] = 成员唯一标识，避免同毫秒并发请求互相覆盖
     * 返回 1 表示放行，0 表示拒绝。
     */
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
            """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local member = ARGV[4]
            local windowStart = now - window * 1000
            redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, now, member)
                redis.call('PEXPIRE', key, window * 1000 + 1000)
                return 1
            else
                return 0
            end
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public SlidingWindowRateLimitUtil(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断限流组件是否可用。
     *
     * @return Redis 客户端已注入时返回 true
     */
    public boolean isAvailable() {
        return redisTemplate != null;
    }

    /**
     * 尝试获取一次请求配额。
     *
     * @param rule 限流规则，不可为 null
     * @return 允许通过返回 true；超出限额返回 false；Redis 异常时降级返回 true
     */
    public boolean tryAcquire(RateLimitRule rule) {
        if (rule == null || rule.limit() <= 0 || rule.windowSeconds() <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        String member = now + "-" + Thread.currentThread().getId()
                + "-" + Integer.toHexString(System.identityHashCode(rule));
        try {
            Long allowed = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(rule.key()),
                    String.valueOf(rule.windowSeconds()),
                    String.valueOf(rule.limit()),
                    String.valueOf(now),
                    member
            );
            return allowed != null && allowed == 1L;
        } catch (Exception e) {
            // Redis 异常时降级放行，但必须记录完整堆栈便于排查
            log.warn("[GATEWAY] 限流 Redis 异常，降级放行: dimension={}, key={}",
                    rule.dimension(), rule.key(), e);
            return true;
        }
    }
}
