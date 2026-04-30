package com.ticket.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * 网关限流配置类
 * 提供限流所需的 KeyResolver Bean
 */
@Configuration
public class RateLimitConfig {

    /**
     * 用户级别的限流键解析器
     * 优先从认证信息中获取用户ID，否则使用IP地址作为备用
     * 
     * 使用说明：
     * 1. 当用户已登录（JWT token有效）时，使用用户ID作为限流键
     * 2. 当用户未登录时，使用客户端IP地址作为限流键
     * 
     * 在 application.yml 中引用：key-resolver: "#{@userKeyResolver}"
     */
    @Bean(name = "userKeyResolver")
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // 优先尝试从认证信息中获取用户ID
            return exchange.getPrincipal()
                    .map(principal -> {
                        if (principal.getName() != null && !principal.getName().isEmpty()) {
                            // 使用用户ID作为限流键
                            return principal.getName();
                        }
                        // 如果认证信息中没有用户ID，回退到IP地址
                        return getClientIp(exchange);
                    })
                    .defaultIfEmpty(getClientIp(exchange));
        };
    }

    //和上面一样，只不过是备用，因为两者只能获取一个
    // Spring Cloud Gateway 的 RequestRateLimiter
    // 配置目前不支持直接通过 @Qualifier 引用多个 KeyResolver，只能指定一个 Bean 名称
    /**
     * IP地址级别的限流键解析器（备用）
     * 始终使用客户端IP地址作为限流键
     * 
     * 使用说明：适用于不需要用户认证的公共接口限流
     */
//    @Bean
//    public KeyResolver ipKeyResolver() {
//        return exchange -> Mono.just(getClientIp(exchange));
//    }

    /**
     * 获取客户端真实IP地址
     * 支持通过 X-Forwarded-For 等代理头获取
     */
    private String getClientIp(org.springframework.web.server.ServerWebExchange exchange) {
        // 1. 优先从 X-Forwarded-For 头获取（经过代理时）
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For 可能包含多个IP，取第一个
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }

        // 2. 从 X-Real-IP 头获取
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // 3. 最后使用远程地址
        String remoteAddress = exchange.getRequest().getRemoteAddress() != null 
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() 
                : "unknown";
        
        // 处理本地地址
        if ("0:0:0:0:0:0:0:1".equals(remoteAddress) || "127.0.0.1".equals(remoteAddress)) {
            return "127.0.0.1";
        }
        
        return remoteAddress;
    }
}