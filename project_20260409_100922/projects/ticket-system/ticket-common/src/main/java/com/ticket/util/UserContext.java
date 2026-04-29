package com.ticket.util;

/**
 * 用户上下文工具类
 * 用于在当前线程中存储和获取当前登录用户ID、员工ID、角色ID和用户类型
 */
public class UserContext {

    /**
     * 上下文数据持有类
     */
    private static class Context {
        Long userId;
        Long employeeId;
        String userType;
        Long roleId;
    }

    // 使用单个 ThreadLocal 存储所有上下文数据
    private static final ThreadLocal<Context> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 获取当前上下文，如果不存在则返回 null
     */
    private static Context getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取或创建当前上下文（如果不存在则创建）
     */
    private static Context getOrCreateContext() {
        Context ctx = CONTEXT_HOLDER.get();
        if (ctx == null) {
            ctx = new Context();
            CONTEXT_HOLDER.set(ctx);
        }
        return ctx;
    }

    /**
     * 设置当前用户ID（用户类型为"user"）
     */
    public static void setCurrentUserId(Long userId) {
        Context ctx = getOrCreateContext();
        ctx.userId = userId;
        ctx.employeeId = null;
        ctx.userType = JwtUtil.USER_TYPE_USER;
    }

    /**
     * 设置当前员工ID（用户类型为"employee"）
     */
    public static void setCurrentEmployeeId(Long employeeId) {
        Context ctx = getOrCreateContext();
        ctx.userId = null;
        ctx.employeeId = employeeId;
        ctx.userType = JwtUtil.USER_TYPE_EMPLOYEE;
    }

    /**
     * 设置当前角色ID
     */
    public static void setCurrentRoleId(Long roleId) {
        Context ctx = getOrCreateContext();
        ctx.roleId = roleId;
    }

    /**
     * 获取当前用户ID（仅当用户类型为"user"时返回）
     */
    public static Long getCurrentUserId() {
        Context ctx = getContext();
        return ctx != null ? ctx.userId : null;
    }

    /**
     * 获取当前员工ID（仅当用户类型为"employee"时返回）
     */
    public static Long getCurrentEmployeeId() {
        Context ctx = getContext();
        return ctx != null ? ctx.employeeId : null;
    }

    /**
     * 获取当前用户类型 ("user" 或 "employee")
     */
    public static String getCurrentUserType() {
        Context ctx = getContext();
        return ctx != null ? ctx.userType : null;
    }

    /**
     * 获取当前角色ID
     */
    public static Long getCurrentRoleId() {
        Context ctx = getContext();
        return ctx != null ? ctx.roleId : null;
    }

    /**
     * 检查是否有登录用户（包括员工）
     */
    public static boolean hasLoginUser() {
        Context ctx = getContext();
        return ctx != null && (ctx.userId != null || ctx.employeeId != null);
    }

    /**
     * 检查当前是否为员工登录
     */
    public static boolean isEmployeeLogin() {
        Context ctx = getContext();
        return ctx != null && JwtUtil.USER_TYPE_EMPLOYEE.equals(ctx.userType);
    }

    /**
     * 检查当前是否为普通用户登录
     */
    public static boolean isUserLogin() {
        Context ctx = getContext();
        return ctx != null && JwtUtil.USER_TYPE_USER.equals(ctx.userType);
    }

    /**
     * 清除所有上下文
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}