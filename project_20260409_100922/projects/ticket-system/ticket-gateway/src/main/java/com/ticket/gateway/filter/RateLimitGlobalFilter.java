package com.ticket.gateway.filter;

import com.ticket.gateway.config.GatewayProperties;
import com.ticket.gateway.support.RateLimitRule;
import com.ticket.gateway.support.SlidingWindowRateLimitUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多维度滑动窗口限流 Filter。
 * <p>
 * 限流维度按从严到宽依次校验，任一维度超限即拒绝：
 * <ol>
 *     <li>IP 黑名单：直接拒绝</li>
 *     <li>下单接口专用限流：单用户每分钟下单次数</li>
 *     <li>路径级限流：按配置的路径前缀最长匹配</li>
 *     <li>服务级限流：按目标服务名 + 用户ID/IP</li>
 *     <li>用户级限流：单用户每秒请求数</li>
 *     <li>IP 级限流：单 IP 每秒请求数</li>
 *     <li>全局限流：整个网关每秒总请求数</li>
 * </ol>
 * Redis 不可用时限流自动降级（放行所有请求）。
 */
@Slf4j
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final String RATE_LIMIT_REJECT_BODY =
            "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null,\"timestamp\":0}";

    private static final String BLACKLIST_REJECT_BODY =
            "{\"code\":403,\"message\":\"访问被拒绝\",\"data\":null,\"timestamp\":0}";

    private final GatewayProperties properties;
    private final SlidingWindowRateLimitUtil rateLimitUtil;

    public RateLimitGlobalFilter(GatewayProperties properties,
                                 @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.rateLimitUtil = new SlidingWindowRateLimitUtil(redisTemplate);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isRateLimitEnabled() || !rateLimitUtil.isAvailable()) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        if (properties.getBlacklistIps().contains(clientIp)) {
            log.warn("[GATEWAY] IP 黑名单拒绝: ip={}", clientIp);
            return reject(exchange, HttpStatus.FORBIDDEN, BLACKLIST_REJECT_BODY);
        }

        List<RateLimitRule> rules = buildRules(exchange, clientIp);
        for (RateLimitRule rule : rules) {
            if (!rateLimitUtil.tryAcquire(rule)) {
                log.warn("[GATEWAY] 限流拒绝: dimension={}, key={}, limit={}/{}s",
                        rule.dimension(), rule.key(), rule.limit(), rule.windowSeconds());
                return reject(exchange, HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_REJECT_BODY);
            }
        }
        return chain.filter(exchange);
    }

    /**
     * 构建当前请求需要校验的全部限流规则。
     *
     * @param exchange 当前请求上下文
     * @param clientIp 已解析出的客户端 IP
     * @return 按从严到宽排序的限流规则列表
     */
    private List<RateLimitRule> buildRules(ServerWebExchange exchange, String clientIp) {
        String path = exchange.getRequest().getPath().value();
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String identity = (userId == null || userId.isBlank()) ? clientIp : userId;

        List<RateLimitRule> rules = new ArrayList<>();
        addOrderRule(rules, path, identity);
        addPathRule(rules, path, identity);
        addServiceRule(rules, exchange, identity);
        addIdentityRules(rules, userId, clientIp);
        addGlobalRule(rules);
        return rules;
    }

    /** 下单接口专用限流：仅对下单写操作生效，排除排队结果轮询等只读路径 */
    private void addOrderRule(List<RateLimitRule> rules, String path, String identity) {
        long limit = properties.getOrderPerUserPerMinute();
        if (limit <= 0 || !path.startsWith(properties.getOrderPathPrefix())) {
            return;
        }
        boolean excluded = properties.getOrderExcludePaths().stream().anyMatch(path::startsWith);
        if (excluded) {
            return;
        }
        rules.add(new RateLimitRule("order", "gw:rl:order:" + identity,
                limit, properties.getOrderWindowSeconds()));
    }

    /** 路径级限流：按配置的路径前缀做最长匹配 */
    private void addPathRule(List<RateLimitRule> rules, String path, String identity) {
        String matched = null;
        for (Map.Entry<String, Long> entry : properties.getPathLimits().entrySet()) {
            if (path.startsWith(entry.getKey())
                    && (matched == null || entry.getKey().length() > matched.length())) {
                matched = entry.getKey();
            }
        }
        if (matched == null) {
            return;
        }
        Long limit = properties.getPathLimits().get(matched);
        if (limit == null || limit <= 0) {
            return;
        }
        rules.add(new RateLimitRule("path", "gw:rl:path:" + matched + ":" + identity,
                limit, properties.getPathWindowSeconds()));
    }

    /** 服务级限流：按目标服务名 + 用户ID/IP */
    private void addServiceRule(List<RateLimitRule> rules, ServerWebExchange exchange, String identity) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return;
        }
        String serviceName = extractServiceName(route.getUri());
        Long limit = properties.getRouteRateLimits().get(serviceName);
        if (limit == null || limit <= 0) {
            return;
        }
        rules.add(new RateLimitRule("service", "gw:rl:svc:" + serviceName + ":" + identity,
                limit, properties.getGlobalWindowSeconds()));
    }

    /** 用户级与 IP 级限流 */
    private void addIdentityRules(List<RateLimitRule> rules, String userId, String clientIp) {
        if (properties.getUserRps() > 0 && userId != null && !userId.isBlank()) {
            rules.add(new RateLimitRule("user", "gw:rl:user:" + userId,
                    properties.getUserRps(), properties.getUserWindowSeconds()));
        }
        if (properties.getIpRps() > 0) {
            rules.add(new RateLimitRule("ip", "gw:rl:ip:" + clientIp,
                    properties.getIpRps(), properties.getIpWindowSeconds()));
        }
    }

    /** 全局限流：整个网关每秒总请求数 */
    private void addGlobalRule(List<RateLimitRule> rules) {
        if (properties.getGlobalRps() <= 0) {
            return;
        }
        rules.add(new RateLimitRule("global", "gw:rl:global",
                properties.getGlobalRps(), properties.getGlobalWindowSeconds()));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String body) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String extractServiceName(URI uri) {
        return uri.getHost();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String xri = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xri != null && !xri.isEmpty()) {
            return xri;
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 7;
    }
}
