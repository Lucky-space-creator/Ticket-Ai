package com.ticket.admin.config;

import com.ticket.admin.exception.AdminBizException;
import com.ticket.util.ResponseUtil;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AdminGlobalExceptionHandler {

    /**
     * 处理业务异常
     * @param e 业务异常
     * @return 错误信息
     */
    @ExceptionHandler(AdminBizException.class)
    public ResponseUtil.Result<?> handleAdminBiz(AdminBizException e) {
        return ResponseUtil.error(e.getMessage());
    }
}
