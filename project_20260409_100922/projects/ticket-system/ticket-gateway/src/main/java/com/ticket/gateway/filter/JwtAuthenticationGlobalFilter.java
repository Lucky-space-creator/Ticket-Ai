package com.ticket.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

/**
 * 入口 JWT 校验（与业务服务 {@code JwtUtil} 使用同一默认密钥 {@code jwt.secret}）。
 * 匿名路径见 {@link #isAnonymous(String, HttpMethod)}。
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Value("${jwt.secret:ticket-system-secret-key-2024-please-change-in-production}")
    private String secret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        if (method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getPath();
        if (isAnonymous(path, method)) {
            return chain.filter(exchange);
        }

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
        } catch (Exception e) {
            return unauthorized(exchange.getResponse());
        }

        return chain.filter(exchange);
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
