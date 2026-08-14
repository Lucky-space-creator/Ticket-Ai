package com.ticket.customer.config;

import com.ticket.util.JwtUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器：
 * <ul>
 *   <li>若连接 URL 携带 query token，则校验 JWT 并把解析出的用户/坐席身份写入会话属性，
 *       供 {@code ChatWebSocketHandler} 替代不可信的客户端自报身份。</li>
 *   <li>未携带 token 时（兼容当前前端）不阻断连接，仅记录审计日志。</li>
 * </ul>
 */
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsAuthHandshakeInterceptor.class);

    public static final String ATTR_USER_ID = "wsUserId";
    public static final String ATTR_EMPLOYEE_ID = "wsEmployeeId";

    @Resource
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI().getQuery());
        if (token != null && !token.isBlank()) {
            try {
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    String userType = jwtUtil.getUserTypeFromToken(token);
                    if (userId != null) {
                        attributes.put(ATTR_USER_ID, userId);
                        if (JwtUtil.USER_TYPE_EMPLOYEE.equals(userType)) {
                            Long employeeId = jwtUtil.getEmployeeIdFromToken(token);
                            if (employeeId != null) {
                                attributes.put(ATTR_EMPLOYEE_ID, employeeId);
                            }
                        }
                    }
                    log.info("WebSocket 握手鉴权成功: userType={}, userId={}", userType, userId);
                } else {
                    log.warn("WebSocket 握手 token 校验失败，按匿名连接处理");
                }
            } catch (Exception e) {
                log.warn("WebSocket 握手 token 解析异常，按匿名连接处理: {}", e.getMessage());
            }
        } else {
            log.debug("WebSocket 握手未携带 token，使用客户端自报身份（建议前端补充 token）");
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    private String extractToken(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && ("token".equals(kv[0]) || "Authorization".equals(kv[0]))) {
                return kv[1];
            }
        }
        return null;
    }
}
