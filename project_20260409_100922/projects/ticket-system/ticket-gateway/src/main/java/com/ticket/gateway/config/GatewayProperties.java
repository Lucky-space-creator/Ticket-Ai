package com.ticket.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 网关配置属性，对应 Nacos 中 gateway.* 前缀。
 * <p>
 * 通过 {@link org.springframework.boot.context.properties.EnableConfigurationProperties} 注册，
 * 确保在 Nacos 配置加载完成后才绑定。
 */
@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /** 是否启用限流 */
    private boolean rateLimitEnabled = true;

    /** 是否启用 JWT 鉴权 */
    private boolean securityEnabled = true;

    /** 全局每秒允许的总请求数 */
    private long globalRps = 10000;

    /** 限流窗口大小（秒） */
    private int globalWindowSeconds = 1;

    /** 各服务的每秒请求数限制，key 为服务名 */
    private Map<String, Long> routeRateLimits = new HashMap<>();
}
