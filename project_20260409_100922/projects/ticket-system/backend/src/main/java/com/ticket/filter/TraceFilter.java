package com.ticket.filter;

import cn.hutool.core.lang.UUID;
import com.ticket.util.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全链路追踪过滤器
 * 为每个HTTP请求生成/提取TraceID，放入ThreadLocal和MDC
 * 执行顺序：最高优先级+10（确保在其他过滤器之前执行）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceFilter extends OncePerRequestFilter {

    /**
     * 拦截所有请求，生成TraceID，并设置到ThreadLocal和MDC中
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
                                    throws ServletException, IOException {

        String traceId = request.getHeader(TraceContext.TRACE_HEADER);

        // 如果请求头没有TraceID，则生成一个新的
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 同时设置到 ThreadLocal 和 MDC
        TraceContext.setTraceId(traceId);
        MDC.put(TraceContext.TRACE_ID_KEY, traceId);

        // 将TraceID写入响应头，供前端获取关联
        response.setHeader(TraceContext.TRACE_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 确保清理，防止线程复用导致数据污染
            TraceContext.clear();
            MDC.remove(TraceContext.TRACE_ID_KEY);
        }
    }

    /**
     * 排除健康检查等不需要追踪的路径
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator/health")
            || path.equals("/actuator/info")
            || path.startsWith("/druid/")
            || path.endsWith(".js")
            || path.endsWith(".css")
            || path.endsWith(".ico")
            || path.endsWith(".png")
            || path.endsWith(".jpg");
    }
}
