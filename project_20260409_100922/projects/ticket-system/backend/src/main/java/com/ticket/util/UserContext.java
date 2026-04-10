package com.ticket.util;

/**
 * 用户上下文工具类
 * 用于在当前线程中存储和获取当前登录用户ID
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前用户ID
     */
    public static void setCurrentUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 清除当前用户ID
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }

    /**
     * 检查是否有登录用户
     */
    public static boolean hasLoginUser() {
        return USER_ID_HOLDER.get() != null;
    }
}
