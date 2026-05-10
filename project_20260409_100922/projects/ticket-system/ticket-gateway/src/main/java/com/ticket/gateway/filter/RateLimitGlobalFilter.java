package com.ticket.gateway.filter;

import com.ticket.gateway.config.GatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 按服务维度的滑动窗口限流 Filter。
 * <p>
 * 限流 key = 服务名 + ":" + 用户ID（或 IP）。
 * Redis 不可用时限流自动降级（放行所有请求）。
 */
@Slf4j
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayProperties properties;
    private final StringRedisTemplate redisTemplate;

    /**
     * Redis Lua 脚本：滑动窗口计数器
     * KEYS[1] = 限流 key
     * ARGV[1] = 窗口大小（秒）
     * ARGV[2] = 最大请求数
     * ARGV[3] = 当前时间戳（毫秒）
     * 返回 1 = 放行，0 = 拒绝
     */
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
            """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local windowStart = now - window * 1000
            redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, now, now .. '-' .. math.random())
                redis.call('EXPIRE', key, window)
                return 1
            else
                return 0
            end
            """,
            Long.class
    );

    public RateLimitGlobalFilter(GatewayProperties properties,
                                 @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isRateLimitEnabled() || redisTemplate == null) {
            return chain.filter(exchange);
        }

        // 获取目标服务名
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }
        String serviceName = extractServiceName(route.getUri());
        Long serviceLimit = properties.getRouteRateLimits().get(serviceName);
        if (serviceLimit == null || serviceLimit <= 0) {
            return chain.filter(exchange);
        }

        // 限流 key：服务名 + 用户ID/IP
        String identity = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (identity == null || identity.isBlank()) {
            identity = getClientIp(exchange);
        }
        String rateLimitKey = "gw:rl:" + serviceName + ":" + identity;
        int windowSeconds = properties.getGlobalWindowSeconds();
        long now = System.currentTimeMillis();

        Long allowed;
        try {
            allowed = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(rateLimitKey),
                    String.valueOf(windowSeconds),
                    String.valueOf(serviceLimit),
                    String.valueOf(now)
            );
        } catch (Exception e) {
            // Redis 异常时降级放行
            log.warn("[GATEWAY] 限流 Redis 异常，降级放行: service={}, error={}", serviceName, e.getMessage());
            return chain.filter(exchange);
        }

        if (allowed != null && allowed == 1L) {
            return chain.filter(exchange);
        }

        log.warn("[GATEWAY] 限流拒绝: service={}, key={}", serviceName, identity);
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\",\"data\":null,\"timestamp\":0}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String extractServiceName(URI uri) {
        return uri.getHost();
    }

    private String getClientIp(ServerWebExchange exchange) {
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
