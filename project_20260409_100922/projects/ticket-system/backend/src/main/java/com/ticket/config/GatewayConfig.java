package com.ticket.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关层配置（限流、安全防护参数）
 * 所有可调参数集中管理，便于不同环境灵活配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayConfig {

    /**
     * 是否启用限流
     */
    @Value("${gateway.rate-limit-enabled:true}")
    private boolean rateLimitEnabled;

    /**
     * 全局默认限流：每秒最大请求数
     */
    @Value("${gateway.global-rps:10000}")
    private int globalRps;

    /**
     * 全局滑动窗口时间（秒）
     */
    @Value("${gateway.global-window-seconds:1}")
    private int globalWindowSeconds;

    /**
     * 单IP限流：每秒最大请求数
     */
    @Value("${gateway.ip-rps:20}")
    private int ipRps;

    /**
     * 单IP滑动窗口时间（秒）
     */
    @Value("${gateway.ip-window-seconds:1}")
    private int ipWindowSeconds;

    /**
     * 单用户限流：每秒最大请求数
     */
    @Value("${gateway.user-rps:1000}")
    private int userRps;

    /**
     * 用户滑动窗口时间（秒）
     */
    @Value("${gateway.user-window-seconds:1}")
    private int userWindowSeconds;

    /**
     * 下单接口专用限流：单用户每分钟最多下单次数
     */
    @Value("${gateway.order-per-user-per-minute:5}")
    private int orderPerUserPerMinute;

    /**
     * 下单接口限流窗口（秒）
     */
    @Value("${gateway.order-window-seconds:60}")
    private int orderWindowSeconds;

    /**
     * 是否启用安全过滤器
     */
    @Value("${gateway.security-enabled:true}")
    private boolean securityEnabled;

    /**
     * IP黑名单（支持CIDR格式，如 192.168.1.0/24）
     */
    private List<String> blacklistIps = List.of();

    /**
     * 各接口路径的独立限流配置
     * key: Ant路径匹配 (如 /api/orders)
     * value: 每秒请求数限制
     */
    private Map<String, Integer> pathLimits = new HashMap<>();

    /**
     * 需要严格限流的接口路径（Ant匹配）
     * 这些接口使用更严格的限流策略
     */
    private List<String> strictPaths = Arrays.asList(
            "/api/orders/**",
            "/api/auth/login"
    );
}
