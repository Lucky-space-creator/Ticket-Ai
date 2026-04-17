package com.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.OperationLog;

import java.util.List;

/**
 * 操作日志服务接口
 */
public interface OperationLogService extends IService<OperationLog> {

    /**
     * 记录操作日志
     */
    void log(OperationLog log);

    /**
     * 查询操作日志
     */
    List<OperationLog> queryLogs(Long userId, String operation, String module, String startTime, String endTime);
}