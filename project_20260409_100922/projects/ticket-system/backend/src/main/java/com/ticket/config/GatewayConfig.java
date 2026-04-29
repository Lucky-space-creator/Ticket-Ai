package com.ticket.config;

import lombok.Data;
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
    private boolean rateLimitEnabled = true;

    /**
     * 全局默认限流：每秒最大请求数
     */
    private int globalRps = 100;

    /**
     * 全局滑动窗口时间（秒）
     */
    private int globalWindowSeconds = 1;

    /**
     * 单IP限流：每秒最大请求数
     */
    private int ipRps = 20;

    /**
     * 单IP滑动窗口时间（秒）
     */
    private int ipWindowSeconds = 1;

    /**
     * 单用户限流：每秒最大请求数
     */
    private int userRps = 10;

    /**
     * 用户滑动窗口时间（秒）
     */
    private int userWindowSeconds = 1;

    /**
     * 下单接口专用限流：单用户每分钟最多下单次数
     */
    private int orderPerUserPerMinute = 5;

    /**
     * 下单接口限流窗口（秒）
     */
    private int orderWindowSeconds = 60;

    /**
     * 是否启用安全过滤器
     */
    private boolean securityEnabled = true;

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
