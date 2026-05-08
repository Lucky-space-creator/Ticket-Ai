package com.ticket.operationlog;

/**
 * 操作日志业务模块，与 {@link com.ticket.annotation.OperationLog#module()} 对应；
 * 持久化与展示使用 {@link #displayName}，需与历史数据、筛选条件保持一致。
 */
public enum Module {

    ROLE_PERMISSION("角色权限"),
    TICKET_STOCK("票务库存"),
    TRAIN("车次管理"),
    AUTH("认证"),
    EMPLOYEE("员工管理"),
    USER("用户管理"),
    USER_AUTH("用户认证"),
    ORDER("订单");

    private final String displayName;

    Module(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
