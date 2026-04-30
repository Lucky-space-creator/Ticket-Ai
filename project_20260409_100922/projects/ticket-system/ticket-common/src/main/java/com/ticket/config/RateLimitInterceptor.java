package com.ticket.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.util.ResponseUtil;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis滑动窗口的限流拦截器
 * 支持三层限流：全局 → IP级别 → 用户级别
 * 支持特定路径的独立限流配置（如下单接口更严格）
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    @Resource
    private GatewayConfig gatewayConfig;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private ObjectMapper objectMapper;

    /** Redis Lua滑动窗口脚本 */
    private static final String SLIDING_WINDOW_LUA =
            "local key = KEYS[1] " +
            "local window = tonumber(ARGV[1]) " +
            "local limit = tonumber(ARGV[2]) " +
            "local now = redis.call('TIME') " +
            "local currentTime = now[1] + now[2] / 1000000 " +
            "windowStart = currentTime - window * 1000000 " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, windowStart) " +
            "local count = redis.call('ZCARD', key) " +
            "if count < limit then " +
            "    redis.call('ZADD', key, currentTime, currentTime .. ':' .. math.random()) " +
            "    redis.call('EXPIRE', key, window) " +
            "    return {1, limit - count - 1} " +
            "else " +
            "    return {0, 0} " +
            "end";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 未启用则直接放行
        if (!gatewayConfig.isRateLimitEnabled()) {
            return true;
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // OPTIONS请求放行（CORS预检）
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        try {
            // L1: 全局限流
            if (!checkLimit("rate:global:", gatewayConfig.getGlobalRps(), gatewayConfig.getGlobalWindowSeconds())) {
                writeLimitExceeded(response, "系统繁忙，请稍后重试");
                return false;
            }

            // L2: IP级别限流
            String clientIp = getClientIp(request);
            if (!checkLimit("rate:ip:" + clientIp + ":", gatewayConfig.getIpRps(), gatewayConfig.getIpWindowSeconds())) {
                writeLimitExceeded(response, "操作过于频繁，请稍后再试");
                logger.warn("IP限流触发: ip={}, uri={}", clientIp, uri);
                return false;
            }

            // L3: 用户级别限流（从JWT Token解析userId）
            Long userId = extractUserIdFromToken(request);
            if (userId != null) {
                int userRps = getUserSpecificLimit(uri);
                if (!checkLimit("rate:user:" + userId + ":", userRps, gatewayConfig.getUserWindowSeconds())) {
                    writeLimitExceeded(response, "操作过于频繁，请稍后再试");
                    logger.warn("用户限流触发: userId={}, uri={}", userId, uri);
                    return false;
                }
                // 下单接口专用严格限流
                if (isOrderCreateRequest(uri, method)) {
                    String orderKey = "rate:order:user:" + userId + ":";
                    if (!checkLimit(orderKey, gatewayConfig.getOrderPerUserPerMinute(), gatewayConfig.getOrderWindowSeconds())) {
                        writeLimitExceeded(response, "下单过于频繁，请稍后再试");
                        logger.warn("下单限流触发: userId={}, uri={}", userId, uri);
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("限流检查异常，默认放行: uri={}, error={}", uri, e.getMessage());
            return true; // 限流组件故障时放行，避免影响正常业务
        }
    }

    /**
     * 执行滑动窗口限流检查
     * @param keyPrefix Redis键前缀
     * @param limit 窗口内最大请求数
     * @param windowSeconds 窗口大小（秒）
     * @return true=放行, false=拒绝
     */
    private boolean checkLimit(String keyPrefix, int limit, int windowSeconds) {
        if (limit <= 0) {
            return true;
        }
        String key = keyPrefix + "sliding";
        Object result = redisUtil.executeScript(SLIDING_WINDOW_LUA,
                java.util.Arrays.asList(key),
                new Object[]{windowSeconds, limit});
        if (result instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) result;
            if (!list.isEmpty() && list.get(0) instanceof Long) {
                return ((Long) list.get(0)) == 1;
            }
        }
        return true;
    }

    /**
     * 获取特定路径的用户级限流阈值
     */
    private int getUserSpecificLimit(String uri) {
        for (String path : gatewayConfig.getPathLimits().keySet()) {
            if (matchAntPath(path, uri)) {
                return gatewayConfig.getPathLimits().get(path);
            }
        }
        // 检查是否是严格限制路径
        for (String strictPath : gatewayConfig.getStrictPaths()) {
            if (matchAntPath(strictPath, uri)) {
                return Math.min(gatewayConfig.getUserRps(), gatewayConfig.getUserRps() / 2 + 1);
            }
        }
        return gatewayConfig.getUserRps();
    }

    /**
     * 判断是否为创建订单请求
     */
    private boolean isOrderCreateRequest(String uri, String method) {
        return "POST".equalsIgnoreCase(method)
                && (uri.startsWith("/api/orders"));
    }

    /**
     * 获取客户端真实IP（支持反向代理场景）
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headerNames = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headerNames) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 取第一个IP（多级代理时第一个为真实IP）
                return ip.contains(",") ? ip.split(",")[0].trim() : ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 从JWT Token中提取用户ID（轻量级解析，不做完整验证）
     */
    private Long extractUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authHeader.substring(7);
            // JWT的Payload部分是第二段（base64url编码）
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(payload);
            if (node.has("userId")) {
                return node.get("userId").asLong();
            }
            if (node.has("sub")) {
                try { return node.get("sub").asLong(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            logger.debug("从Token解析userId失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 简单的Ant路径匹配
     */
    private boolean matchAntPath(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
        String regex = pattern.replace("**", ".+").replace("*", "[^/]*");
        return path.matches(regex);
    }

    /**
     * 写入限流拒绝响应
     */
    private void writeLimitExceeded(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        ResponseUtil.Result<?> result = ResponseUtil.error(429, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}