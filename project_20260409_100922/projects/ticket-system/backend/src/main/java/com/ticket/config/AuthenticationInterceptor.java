package com.ticket.config;

import com.ticket.util.JwtUtil;
import com.ticket.util.RedisUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器
 * 在请求处理前从Token中提取用户ID并存储到UserContext
 */
@Slf4j
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private RedisUtil redisUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId != null) {
                // 验证用户是否在缓存中存在（可选，增强安全性）
                String userInfoKey = String.format("user:token:%d", userId);
                String userInfo = redisUtil.get(userInfoKey);
                if (userInfo != null) {
                    log.info("用户信息已验证，用户ID: {}", userId);
                    //解析token中用户信息，存储到UserContext
                    userId = jwtUtil.getUserIdFromToken(token);
                    UserContext.setCurrentUserId(userId);
                } else {
                    log.warn("用户信息不在缓存中，可能已过期，用户ID: {}", userId);
                    // 仍然设置userId，因为JWT有效，但需要重新登录
                    UserContext.setCurrentUserId(userId);
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求完成后清除上下文，防止内存泄漏
        UserContext.clear();
    }
}
