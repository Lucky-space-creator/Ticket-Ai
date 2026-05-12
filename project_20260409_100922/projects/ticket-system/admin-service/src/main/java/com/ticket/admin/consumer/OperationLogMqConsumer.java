package com.ticket.admin.consumer;

import com.ticket.admin.mapper.OperationLogMapper;
import com.ticket.dto.mq.OperationLogEvent;
import com.ticket.entity.OperationLog;
import com.ticket.enums.MQTopics;
import com.ticket.util.MQIdempotentUtil;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 操作日志 MQ 消费者：订阅 {@link MQTopics#OPERATION_LOG}（Topic {@code operation-log}），将消息异步落库到 {@code operation_log} 表。
 * <p><b>在架构中的位置</b>：各服务（如 admin-service、user-service）通过 {@code RocketMQProducerService#sendOperationLogEvent}
 * 投递 {@link OperationLogEvent}；仅本消费者负责写库，保证审计数据集中存储、业务线程不被 DB IO 阻塞。</p>
 * <p><b>幂等与重复消费</b>：RocketMQ 在异常重试、网络抖动时可能重复投递。以事件内的 {@code messageId}（生产者生成的 UUID）
 * 作为幂等键，配合 {@link MQIdempotentUtil} 写入 Redis；已消费过的 messageId 直接跳过，避免表中产生重复审计行。</p>
 * <p><b>集群模式</b>：{@link MessageModel#CLUSTERING} 表示同一消费组内多实例负载均衡，每条消息只被其中一个实例处理。</p>
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopics.OPERATION_LOG,
        consumerGroup = "operation-log-consumer-group",
        messageModel = MessageModel.CLUSTERING
)
public class OperationLogMqConsumer implements RocketMQListener<OperationLogEvent> {

    /**
     * 以下长度与 {@code database/init.sql} 中 {@code operation_log} 表字段定义对齐，
     * 超长内容在入库前截断，防止因字段溢出导致整条消息消费失败、进入无限重试。
     */
    private static final int MAX_OPERATION = 100;
    private static final int MAX_MODULE = 50;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_METHOD = 10;
    private static final int MAX_URL = 500;
    private static final int MAX_USERNAME = 50;
    private static final int MAX_IP = 50;
    private static final int MAX_UA = 500;
    /** request_params 为 TEXT，这里设上限避免单条日志过大 */
    private static final int MAX_PARAMS = 8000;
    private static final int MAX_ERROR = 4000;

    @Resource
    private OperationLogMapper operationLogMapper;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Resource
    private RedisUtil redisUtil;

    /**
     * RocketMQ 回调入口：单条消息一次调用。失败时抛出异常可触发框架重试（受 maxReconsumeTimes 等配置约束）。
     */
    @Override
    public void onMessage(OperationLogEvent event) {
        // messageId 是幂等键，缺失则无法去重，直接丢弃并打告警日志
        if (event == null || !StringUtils.hasText(event.getMessageId())) {
            log.warn("操作日志事件无效，跳过");
            return;
        }
        try {
            // 已处理过则静默返回（成功 ack，不再写库）
            if (idempotentUtil.alreadyConsumed(MQTopics.OPERATION_LOG, event.getMessageId())) {
                return;
            }

            OperationLog row = toEntity(event);
            operationLogMapper.insert(row);
            // 插入成功后再标记已消费，避免 DB 失败却误标记导致丢日志
            idempotentUtil.markConsumed(MQTopics.OPERATION_LOG, event.getMessageId());
            log.debug("operation_log 写入成功 messageId={} module={} op={}",
                    event.getMessageId(), row.getModule(), row.getOperation());
        } catch (DuplicateKeyException e) {
            // 极端情况：并发或表上额外唯一约束冲突，视为已有一条等价记录，标记幂等避免死循环重试
            idempotentUtil.markConsumed(MQTopics.OPERATION_LOG, event.getMessageId());
            log.debug("operation_log 主键或唯一冲突，视为已处理 messageId={}", event.getMessageId());
        } catch (Exception e) {
            log.error("处理操作日志事件失败 messageId={}: {}", event.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 将 MQ 载荷转为 MyBatis 实体。对字符串统一做 {@link #truncate}，对必填列 {@code operation} 做 {@link #require} 兜底。
     */
    private static OperationLog toEntity(OperationLogEvent event) {
        OperationLog operationLog = new OperationLog();
        operationLog.setUserId(event.getUserId());
        operationLog.setUsername(truncate(event.getUsername(), MAX_USERNAME));
        operationLog.setOperation(truncate(require(event.getOperation(), "operation"), MAX_OPERATION));
        operationLog.setModule(truncate(event.getModule(), MAX_MODULE));
        // 描述缺省时用 operation 填充，便于列表展示
        operationLog.setDescription(truncate(
                StringUtils.hasText(event.getDescription()) ? event.getDescription() : event.getOperation(),
                MAX_DESCRIPTION));
        operationLog.setRequestMethod(truncate(event.getRequestMethod(), MAX_METHOD));
        operationLog.setRequestUrl(truncate(event.getRequestUrl(), MAX_URL));
        operationLog.setRequestParams(truncate(event.getRequestParams(), MAX_PARAMS));
        operationLog.setIpAddress(truncate(event.getIpAddress(), MAX_IP));
        operationLog.setUserAgent(truncate(event.getUserAgent(), MAX_UA));
        // 事件里非 1 一律按失败落库，与表注释「1-成功 0-失败」一致
        operationLog.setStatus(event.getStatus() == 1 ? 1 : 0);
        operationLog.setErrorMessage(truncate(event.getErrorMessage(), MAX_ERROR));
        operationLog.setExecutionTime(event.getExecutionTime());
        operationLog.setCreatedAt(event.getCreatedAt() != null ? event.getCreatedAt() : LocalDateTime.now());
        return operationLog;
    }

    /** 表字段 NOT NULL 时使用占位符，避免插入失败 */
    private static String require(String v, String field) {
        if (!StringUtils.hasText(v)) {
            return "UNKNOWN";
        }
        return v;
    }

    /** null/空串保持 null；否则超长截断前缀保留 */
    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
