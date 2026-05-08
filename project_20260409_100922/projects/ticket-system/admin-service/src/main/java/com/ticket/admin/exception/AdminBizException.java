package com.ticket.admin.exception;

/**
 * 管理端业务规则类异常，由 {@link com.ticket.admin.config.AdminGlobalExceptionHandler} 统一转为接口错误响应。
 */
public class AdminBizException extends RuntimeException {

    public AdminBizException(String message) {
        super(message);
    }
}
