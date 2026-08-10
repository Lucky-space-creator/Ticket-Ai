package com.ticket.gateway.support;

/**
 * 单条限流规则。
 *
 * @param dimension     限流维度名称，用于日志定位（如 order/path/service/user/ip/global）
 * @param key           Redis 中的限流 key
 * @param limit         窗口内允许的最大请求数
 * @param windowSeconds 滑动窗口大小（秒）
 */
public record RateLimitRule(String dimension, String key, long limit, int windowSeconds) {
}
