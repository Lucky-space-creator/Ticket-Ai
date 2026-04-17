package com.ticket.config;

import com.ticket.entity.Permission;
import com.ticket.entity.User;
import com.ticket.service.PermissionService;
import com.ticket.service.UserService;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 权限拦截器
 * 检查用户是否有访问当前API的权限
 */
@Slf4j
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    @Resource
    private PermissionService permissionService;

    @Resource
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            // 如果没有用户ID（认证拦截器未设置），直接放行，由认证拦截器处理
            return true;
        }

        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();

        // 白名单：公开API不需要权限检查
        if (isPublicApi(requestUri)) {
            return true;
        }

        // 方案3：super_admin角色直接放行
        User user = userService.getById(userId);
        if (user != null && user.getRoleId() != null && user.getRoleId() == 1) {
            // role_id = 1 是超级管理员（根据初始化SQL）
            return true;
        }

        // 获取用户权限
        List<Permission> userPermissions = permissionService.getPermissionsByUserId(userId);
        
        // 检查是否有匹配的API权限
        boolean hasPermission = userPermissions.stream()
                .filter(permission -> permission.getPermissionType() == 3) // API权限
                .anyMatch(permission -> 
                    permission.getApiMethod().equalsIgnoreCase(requestMethod) &&
                    matchApiPath(permission.getApiPath(), requestUri)
                );

        if (!hasPermission) {
            log.warn("用户 {} 无权限访问 {} {}", userId, requestMethod, requestUri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问该接口\"}");
            return false;
        }

        return true;
    }

    /**
     * 判断是否为公开API
     */
    private boolean isPublicApi(String uri) {
        // 认证相关接口
        if (uri.startsWith("/api/auth/")) {
            return true;
        }
        
        // 车次查询、车站查询等公开接口
        if (uri.startsWith("/api/trains/") || uri.startsWith("/api/stations/")) {
            return true;
        }
        
        // 知识库公开查询
        if (uri.startsWith("/api/knowledge/list")) {
            return true;
        }
        
        // AI客服公开接口
        if (uri.startsWith("/api/chat/")) {
            return true;
        }
        
        // 方案1：用户端自身数据操作接口加入白名单
        // 用户个人信息接口（已认证用户可访问自己的信息）
        if (uri.startsWith("/api/user/")) {
            return true;
        }
        
        // 用户订单和乘客接口（已认证用户可访问自己的数据）
        return uri.startsWith("/api/orders/") || uri.startsWith("/api/passengers/");
    }

    /**
     * 匹配API路径，支持路径参数
     * 例如：/api/admin/users/{id} 匹配 /api/admin/users/123
     */
    private boolean matchApiPath(String pattern, String path) {
        if (pattern.equals(path)) {
            return true;
        }
        
        // 简单处理路径参数：将{xxx}替换为.*
        String regex = pattern.replaceAll("\\{[^/]+\\}", "[^/]+");
        return path.matches(regex);
    }
}