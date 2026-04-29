package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.dto.mq.OperationLogEvent;
import com.ticket.entity.OperationLog;
import com.ticket.mapper.OperationLogMapper;
import com.ticket.service.RocketMQProducerService;
import com.ticket.service.OperationLogService;
import com.ticket.util.MQIdempotentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志服务实现
 * 日志写入通过RocketMQ异步处理，不阻塞业务线程
 */
@Slf4j
@Service
public class OperationLogServiceImpl implements OperationLogService {

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private RocketMQProducerService rocketMQProducerService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    public void log(OperationLog operationLog) {
        try {
            OperationLogEvent event = new OperationLogEvent();
            event.setMessageId(idempotentUtil.generateMessageId());
            event.setUserId(operationLog.getUserId());
            event.setUsername(operationLog.getUsername());
            event.setOperation(operationLog.getOperation());
            event.setModule(operationLog.getModule());
            event.setDescription(operationLog.getDescription());
            event.setRequestMethod(operationLog.getRequestMethod());
            event.setRequestUrl(operationLog.getRequestUrl());
            event.setRequestParams(operationLog.getRequestParams());
            event.setIpAddress(operationLog.getIpAddress());
            event.setUserAgent(operationLog.getUserAgent());
            event.setStatus(operationLog.getStatus() != null ? operationLog.getStatus() : 1);
            event.setErrorMessage(operationLog.getErrorMessage());
            event.setExecutionTime(operationLog.getExecutionTime() != null ? operationLog.getExecutionTime() : 0);
            event.setCreatedAt(LocalDateTime.now());

            rocketMQProducerService.sendOperationLogEvent(event);
        } catch (Exception e) {
            log.error("发送操作日志事件失败", e);
        }
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
        return operationLogMapper.selectList(wrapper);
    }
}