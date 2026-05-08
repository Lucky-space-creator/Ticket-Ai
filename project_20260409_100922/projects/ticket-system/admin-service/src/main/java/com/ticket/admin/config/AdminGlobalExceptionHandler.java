package com.ticket.admin.config;

import com.ticket.admin.exception.AdminBizException;
import com.ticket.util.ResponseUtil;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AdminGlobalExceptionHandler {

    @ExceptionHandler(AdminBizException.class)
    public ResponseUtil.Result<?> handleAdminBiz(AdminBizException e) {
        return ResponseUtil.error(e.getMessage());
    }
}
