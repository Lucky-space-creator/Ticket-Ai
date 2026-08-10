package com.ticket.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网关配置属性，对应 Nacos 中 gateway.* 前缀。
 * <p>
 * 通过 {@link org.springframework.boot.context.properties.EnableConfigurationProperties} 注册，
 * 确保在 Nacos 配置加载完成后才绑定。
 * <p>
 * 限流维度优先级（从严到宽）：IP黑名单 > 路径级限流 > 服务级限流 > 用户级限流 > 全局限流。
 */
@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /** 是否启用限流 */
    private boolean rateLimitEnabled = true;

    /** 是否启用 JWT 鉴权 */
    private boolean securityEnabled = true;

    /** 全局每秒允许的总请求数（所有请求共享一个计数器） */
    private long globalRps = 10000;

    /** 全局限流窗口大小（秒） */
    private int globalWindowSeconds = 1;

    /** 单 IP 每秒最大请求数，防止单点攻击；小于等于 0 表示不限制 */
    private long ipRps = 20;

    /** IP 限流窗口大小（秒） */
    private int ipWindowSeconds = 1;

    /** 单用户每秒最大请求数，防止刷接口；小于等于 0 表示不限制 */
    private long userRps = 1000;

    /** 用户限流窗口大小（秒） */
    private int userWindowSeconds = 1;

    /** 各服务的每秒请求数限制，key 为服务名（如 order-service） */
    private Map<String, Long> routeRateLimits = new HashMap<>();

    /**
     * 特定路径的独立限流，key 为路径前缀（如 /api/orders），value 为窗口内最大请求数。
     * 匹配规则为最长前缀匹配，命中后使用 {@link #pathWindowSeconds} 作为窗口。
     */
    private Map<String, Long> pathLimits = new HashMap<>();

    /** 路径级限流窗口大小（秒） */
    private int pathWindowSeconds = 1;

    /** 下单接口专用限流：单用户每分钟最多下单次数；小于等于 0 表示不限制 */
    private long orderPerUserPerMinute = 5;

    /** 下单接口限流窗口大小（秒） */
    private int orderWindowSeconds = 60;

    /** 下单接口路径前缀，用于匹配 {@link #orderPerUserPerMinute} 规则 */
    private String orderPathPrefix = "/api/orders";

    /**
     * 下单接口中需要排除限流的只读路径前缀（如排队结果轮询）。
     * 这些路径是高频只读请求，不应受下单写操作的严格限流约束。
     */
    private List<String> orderExcludePaths = new ArrayList<>(List.of("/api/orders/queue"));

    /** IP 黑名单，命中直接拒绝 */
    private List<String> blacklistIps = new ArrayList<>();
}
