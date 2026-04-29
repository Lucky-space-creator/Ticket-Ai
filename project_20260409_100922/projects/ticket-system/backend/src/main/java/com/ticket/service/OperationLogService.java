package com.ticket.service;

import com.ticket.entity.OperationLog;

import java.util.List;

/**
 * 操作日志服务接口
 * 日志写入通过RocketMq异步处理
 */
public interface OperationLogService {

    /**
     * 记录操作日志
     */
    void log(OperationLog log);

    /**
     * 查询操作日志
     */
    List<OperationLog> queryLogs(Long userId, String operation, String module, String startTime, String endTime);
}