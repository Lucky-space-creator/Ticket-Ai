package com.ticket.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志事件消息
 * 异步写入操作日志到数据库
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OperationLogEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息唯一ID */
    private String messageId;

    /** 操作用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 操作类型 */
    private String operation;

    /** 操作模块 */
    private String module;

    /** 操作描述 */
    private String description;

    /** 请求方法 */
    private String requestMethod;

    /** 请求URL */
    private String requestUrl;

    /** 请求参数 */
    private String requestParams;

    /** IP地址 */
    private String ipAddress;

    /** 用户代理 */
    private String userAgent;

    /** 操作状态 1-成功 0-失败 */
    private int status;

    /** 错误信息 */
    private String errorMessage;

    /** 执行耗时(ms) */
    private int executionTime;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
