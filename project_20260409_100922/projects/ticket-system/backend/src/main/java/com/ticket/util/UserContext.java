package com.ticket.util;

/**
 * 用户上下文工具类
 * 用于在当前线程中存储和获取当前登录用户ID或员工ID
 */
public class UserContext {

    // 存储当前用户ID的 ThreadLocal
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();
    // 存储当前员工ID的 ThreadLocal
    private static final ThreadLocal<Long> EMPLOYEE_ID_HOLDER = new ThreadLocal<>();
    // 存储当前用户类型的 ThreadLocal
    private static final ThreadLocal<String> USER_TYPE_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前用户ID（用户类型为"user"）
     */
    public static void setCurrentUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
        EMPLOYEE_ID_HOLDER.remove();
        USER_TYPE_HOLDER.set(JwtUtil.USER_TYPE_USER);
    }

    /**
     * 设置当前员工ID（用户类型为"employee"）
     */
    public static void setCurrentEmployeeId(Long employeeId) {
        EMPLOYEE_ID_HOLDER.set(employeeId);
        USER_ID_HOLDER.remove();
        USER_TYPE_HOLDER.set(JwtUtil.USER_TYPE_EMPLOYEE);
    }

    /**
     * 获取当前用户ID（仅当用户类型为"user"时返回）
     */
    public static Long getCurrentUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 获取当前员工ID（仅当用户类型为"employee"时返回）
     */
    public static Long getCurrentEmployeeId() {
        return EMPLOYEE_ID_HOLDER.get();
    }

    /**
     * 获取当前用户类型 ("user" 或 "employee")
     */
    public static String getCurrentUserType() {
        return USER_TYPE_HOLDER.get();
    }

    /**
     * 检查是否有登录用户（包括员工）
     */
    public static boolean hasLoginUser() {
        return USER_ID_HOLDER.get() != null || EMPLOYEE_ID_HOLDER.get() != null;
    }

    /**
     * 检查当前是否为员工登录
     */
    public static boolean isEmployeeLogin() {
        return JwtUtil.USER_TYPE_EMPLOYEE.equals(USER_TYPE_HOLDER.get());
    }

    /**
     * 检查当前是否为普通用户登录
     */
    public static boolean isUserLogin() {
        return JwtUtil.USER_TYPE_USER.equals(USER_TYPE_HOLDER.get());
    }

    /**
     * 清除所有上下文
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
        EMPLOYEE_ID_HOLDER.remove();
        USER_TYPE_HOLDER.remove();
    }
}
