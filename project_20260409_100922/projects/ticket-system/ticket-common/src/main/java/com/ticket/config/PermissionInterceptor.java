package com.ticket.config;

import com.ticket.entity.Permission;
import com.ticket.service.PermissionService;
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();
        
        // 白名单：公开API不需要权限检查
        if (isPublicApi(requestUri)) {
            return true;
        }
        
        // 检查用户类型和身份
        if (UserContext.isEmployeeLogin()) {
            // 员工登录处理
            return handleEmployeeRequest(request, response, requestUri, requestMethod);
        } else if (UserContext.isUserLogin()) {
            // 普通用户登录处理
            return handleUserRequest(request, response, requestUri, requestMethod);
        } else {
            // 未登录用户（认证拦截器未设置上下文），直接放行，由认证拦截器处理
            return true;
        }
    }
    
    /**
     * 处理员工请求
     */
    private boolean handleEmployeeRequest(HttpServletRequest request, HttpServletResponse response, 
                                         String requestUri, String requestMethod) throws Exception {
        // 员工只能访问管理端接口 (/api/admin/**) 和客服工作台接口 (/api/customer-service/**)
        if (!requestUri.startsWith("/api/admin/") && !requestUri.startsWith("/api/customer-service/")) {
            // 非管理接口，拒绝访问
            log.warn("员工尝试访问非管理接口: {}", requestUri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"该用户目前无访问权限\"}");
            return false;
        }
        
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            log.warn("员工ID为空，拒绝访问");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"员工未登录或登录已过期\"}");
            return false;
        }
        
        // 检查是否为超级管理员（角色ID=1）
        Long roleId = UserContext.getCurrentRoleId();
        if (roleId != null && roleId == 1) {
            log.debug("员工 {} 为超级管理员，放行访问 {}", employeeId, requestUri);
            return true;
        }
        
        // 获取当前员工权限（统一使用getCurrentPermissions方法）
        List<Permission> permissions = permissionService.getCurrentPermissions();
        
        // 检查是否有匹配的API权限
        boolean hasPermission = permissions.stream()
                .filter(permission -> permission.getPermissionType() == 3) // API权限
                .anyMatch(permission -> 
                    permission.getApiMethod().equalsIgnoreCase(requestMethod) &&
                    matchApiPath(permission.getApiPath(), requestUri)
                );
        
        if (!hasPermission) {
            log.warn("员工 {} 无权限访问 {} {}", employeeId, requestMethod, requestUri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"该用户目前无访问权限\"}");
            return false;
        }
        
        log.info("员工访问管理/客服接口: {}", requestUri);
        return true;
    }
    
    /**
     * 处理用户请求
     */
    private boolean handleUserRequest(HttpServletRequest request, HttpServletResponse response,
                                     String requestUri, String requestMethod) throws Exception {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            // 如果没有用户ID（认证拦截器未设置），直接放行，由认证拦截器处理
            return true;
        }
        
        // 检查是否为超级管理员（角色ID=1）
        Long roleId = UserContext.getCurrentRoleId();
        if (roleId != null && roleId == 1) {
            log.debug("用户 {} 为超级管理员，放行访问 {}", userId, requestUri);
            return true;
        }
        
        // 获取当前用户权限（统一使用getCurrentPermissions方法）
        List<Permission> permissions = permissionService.getCurrentPermissions();
        
        // 检查是否有匹配的API权限
        boolean hasPermission = permissions.stream()
                .filter(permission -> permission.getPermissionType() == 3) // API权限
                .anyMatch(permission -> 
                    permission.getApiMethod().equalsIgnoreCase(requestMethod) &&
                    matchApiPath(permission.getApiPath(), requestUri)
                );
        
        if (!hasPermission) {
            log.warn("用户 {} 无权限访问 {} {}", userId, requestMethod, requestUri);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"该用户目前无访问权限\"}");
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
        if (uri.startsWith("/api/orders/") || uri.startsWith("/api/passengers/")) {
            return true;
        }
        
        // 管理端订单接口（临时白名单）
        return uri.startsWith("/api/admin/orders");
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