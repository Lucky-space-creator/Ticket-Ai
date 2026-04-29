package com.ticket.consumer;

import com.ticket.dto.mq.OperationLogEvent;
import com.ticket.entity.OperationLog;
import com.ticket.enums.MQTopics;
import com.ticket.mapper.OperationLogMapper;
import com.ticket.util.MQIdempotentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 操作日志消费者
 * 异步写入操作日志到数据库
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopics.OPERATION_LOG,
        consumerGroup = "operation-log-consumer-group"
)
public class OperationLogConsumer implements RocketMQListener<OperationLogEvent> {

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    public void onMessage(OperationLogEvent event) {
        try {
            if (event == null || event.getMessageId() == null) {
                log.warn("收到空操作日志事件，跳过");
                return;
            }
            // 幂等性校验
            if (idempotentUtil.isConsumed(MQTopics.OPERATION_LOG, event.getMessageId())) {
                return;
            }

            // 写入数据库
            OperationLog operationLog = new OperationLog();
            operationLog.setUserId(event.getUserId());
            operationLog.setUsername(event.getUsername());
            operationLog.setOperation(event.getOperation());
            operationLog.setModule(event.getModule());
            operationLog.setDescription(event.getDescription());
            operationLog.setRequestMethod(event.getRequestMethod());
            operationLog.setRequestUrl(event.getRequestUrl());
            operationLog.setRequestParams(event.getRequestParams());
            operationLog.setIpAddress(event.getIpAddress());
            operationLog.setUserAgent(event.getUserAgent());
            operationLog.setStatus(event.getStatus());
            operationLog.setErrorMessage(event.getErrorMessage());
            operationLog.setExecutionTime(event.getExecutionTime());
            operationLog.setCreatedAt(
                    event.getCreatedAt() != null ? event.getCreatedAt() : LocalDateTime.now()
            );

            operationLogMapper.insert(operationLog);
            log.debug("操作日志写入成功: operation={}, module={}",
                    event.getOperation(), event.getModule());
        } catch (DuplicateKeyException e) {
            log.debug("操作日志主键冲突，已存在: {}", event.getOperation());
        } catch (Exception e) {
            log.error("处理操作日志事件失败: {}", e.getMessage(), e);
            throw e; // 抛出异常触发RocketMQ重试
        }
    }
}
