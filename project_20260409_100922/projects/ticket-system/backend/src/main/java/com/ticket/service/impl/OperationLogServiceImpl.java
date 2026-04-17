package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.OperationLog;
import com.ticket.mapper.OperationLogMapper;
import com.ticket.service.OperationLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志服务实现
 */
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public void log(OperationLog log) {
        this.save(log);
    }

    @Override
    public List<OperationLog> queryLogs(Long userId, String operation, String module, String startTime, String endTime) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (operation != null && !operation.isEmpty()) {
            wrapper.eq(OperationLog::getOperation, operation);
        }
        if (module != null && !module.isEmpty()) {
            wrapper.eq(OperationLog::getModule, module);
        }
        if (startTime != null && !startTime.isEmpty()) {
            LocalDateTime start = LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            wrapper.ge(OperationLog::getCreatedAt, start);
        }
        if (endTime != null && !endTime.isEmpty()) {
            LocalDateTime end = LocalDateTime.parse(endTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            wrapper.le(OperationLog::getCreatedAt, end);
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        return this.list(wrapper);
    }
}