package com.ticket.operationlog;

/**
 * 操作日志动作类型，与 {@link com.ticket.annotation.OperationLog#operation()} 对应；
 * 持久化与展示使用 {@link #displayName}。
 */
public enum Operation {

    CREATE_ROLE("创建角色"),
    UPDATE_ROLE("更新角色"),
    UPDATE_ROLE_STATUS("更新角色状态"),
    DELETE_ROLE("删除角色"),
    ASSIGN_PERMISSIONS("分配权限"),

    CREATE_PERMISSION("创建权限"),
    UPDATE_PERMISSION("更新权限"),
    UPDATE_PERMISSION_STATUS("更新权限状态"),
    DELETE_PERMISSION("删除权限"),

    ADJUST_SEATS("调整座席"),
    SALE_SWITCH("开售开关"),

    CREATE_TRAIN("新增车次"),
    UPDATE_TRAIN("更新车次"),
    UPDATE_TRAIN_STATUS("更新车次状态"),
    SET_REMAINING_TICKETS("设置余票"),

    EMPLOYEE_LOGIN_LEGACY("员工登录(旧版)"),
    UPDATE_EMPLOYEE_STATUS("更新员工状态"),

    UPDATE_USER_STATUS("更新用户状态"),
    UPDATE_USER_ROLE("更新用户角色"),

    USER_REGISTER("用户注册"),
    USER_LOGIN("用户登录"),

    ADMIN_ORDER_REFUND("后台退款"),

    EMPLOYEE_LOGIN("员工登录");

    private final String displayName;

    Operation(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
