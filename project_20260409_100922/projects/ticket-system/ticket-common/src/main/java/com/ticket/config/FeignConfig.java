package com.ticket.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Feign 客户端全局配置
 */
@Configuration
public class FeignConfig {

    /**
     * 仅转发与鉴权、链路追踪相关的头，避免把浏览器/网关的杂项头原样复制到内部 RPC。
     */
    private static final Set<String> FORWARD_HEADER_NAMES = new HashSet<>(Arrays.asList(
            "authorization",
            "traceparent",
            "tracestate",
            "x-request-id",
            "x-b3-traceid",
            "x-b3-spanid",
            "x-b3-parentspanid",
            "x-b3-sampled",
            "x-forwarded-for",
            "x-real-ip"
    ));

    /**
     * 请求拦截器：选择性传递请求头（JWT、W3C Trace Context、Zipkin B3 等）
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            // 拦截请求，将请求头中的某些字段传递给下游服务
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    java.util.Enumeration<String> headerNames = request.getHeaderNames();
                    while (headerNames.hasMoreElements()) {
                        String headerName = headerNames.nextElement();
                        if (headerName == null) {
                            continue;
                        }
                        if (FORWARD_HEADER_NAMES.contains(headerName.toLowerCase(Locale.ROOT))) {
                            String headerValue = request.getHeader(headerName);
                            template.header(headerName, headerValue);
                        }
                    }
                }
            }
        };
    }

    /**
     * Feign日志级别配置
     */
    @Bean
    public feign.Logger.Level feignLoggerLevel() {
        return feign.Logger.Level.FULL;
    }
}