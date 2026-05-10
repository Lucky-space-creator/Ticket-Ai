package com.ticket.gateway.filter;

import com.ticket.gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 入口 JWT 校验 + 用户上下文透传 + TraceId 注入。
 * <p>
 * 匿名路径见 {@link #isAnonymous(String, HttpMethod)}。
 * JWT 密钥必须通过 Nacos 配置 {@code jwt.secret} 提供，无默认值。
 * 通过 {@code gateway.security-enabled} 控制是否启用鉴权。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final GatewayProperties gatewayProperties;

    @Value("${jwt.secret:}")
    private String secret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();

        // OPTIONS 预检请求直接放行
        if (method == HttpMethod.OPTIONS) {
            return chain.filter(injectTraceId(exchange));
        }

        // 安全开关关闭时，跳过鉴权但仍然注入 TraceId
        String path = request.getURI().getPath();
        if (!gatewayProperties.isSecurityEnabled() || isAnonymous(path, method)) {
            return chain.filter(injectTraceId(exchange));
        }

        // 密钥未配置时拒绝所有非匿名请求
        if (secret == null || secret.isBlank()) {
            log.error("jwt.secret 未配置，无法进行鉴权");
            return forbidden(exchange.getResponse());
        }

        // 校验 Authorization 头
        List<String> authHeaders = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authHeaders == null || authHeaders.isEmpty()) {
            return unauthorized(exchange.getResponse());
        }
        String auth = authHeaders.get(0);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange.getResponse());
        }

        String rawToken = auth.substring(7).trim();
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(rawToken)
                    .getPayload();

            Date exp = claims.getExpiration();
            if (exp != null && exp.before(new Date())) {
                return unauthorized(exchange.getResponse());
            }

            // 解析成功，将用户信息透传到下游请求头
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", String.valueOf(claims.get("userId")))
                    .header("X-User-Phone", claims.getSubject() != null ? claims.getSubject() : "")
                    .header("X-User-Roles", claims.get("roles") != null ? claims.get("roles", String.class) : "")
                    .build();

            return chain.filter(injectTraceId(exchange.mutate().request(mutatedRequest).build()));
        } catch (Exception e) {
            return unauthorized(exchange.getResponse());
        }
    }

    /**
     * 注入 TraceId：优先复用上游传入的 X-Trace-Id，否则生成新 UUID。
     */
    private ServerWebExchange injectTraceId(ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header("X-Trace-Id", traceId)
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private static boolean isAnonymous(String path, HttpMethod method) {
        if (MATCHER.match("/actuator/health/**", path) || "/actuator/health".equals(path)) {
            return true;
        }
        if (MATCHER.match("/v3/api-docs/**", path) || MATCHER.match("/swagger-ui/**", path)
                || MATCHER.match("/swagger-ui.html", path)) {
            return true;
        }
        if (HttpMethod.GET.equals(method) && MATCHER.match("/api/trains/search", path)) {
            return true;
        }
        if (HttpMethod.POST.equals(method) && (MATCHER.match("/api/auth/login", path)
                || MATCHER.match("/api/auth/register", path)
                || MATCHER.match("/api/admin/auth/login", path))) {
            return true;
        }
        return false;
    }

    private static Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    private static Mono<Void> forbidden(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
