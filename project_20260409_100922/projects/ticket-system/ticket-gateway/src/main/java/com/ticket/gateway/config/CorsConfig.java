package com.ticket.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 网关统一 CORS 配置，下游服务无需重复配置。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许所有域名访问
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        ));
        // 允许所有请求头
        config.setAllowedHeaders(List.of("*"));
        // 允许用户认证和 TraceId 透传
        config.setExposedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                "X-Trace-Id"
        ));
        // 允许携带认证信息
        config.setAllowCredentials(true);
        // 设置3600秒的缓存时间,期间不需要重复预检

        /**
         * 浏览器                             网关
         *   │                                  │
         *   │── OPTIONS /api/orders ──────────►│  ← 预检：我能发 POST 吗？
         *   │◄── 200 + CORS 头 ───────────────│  ← 可以
         *   │                                  │
         *   │── POST /api/orders (真正的请求)──►│  ← 实际请求
         *   │◄── 200 ─────────────────────────│
         *   问题：每次跨域请求都先发一次 OPTIONS，一个页面如果有 10 个跨域接口，
         *   就多 10 个 OPTIONS 请求。白白浪费一半的请求量。
         */
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 匹配所有路径
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
