package com.ticket.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.dto.mq.OrderQueueRequest;
import com.ticket.order.integration.TrainOrderGateway;
import com.ticket.order.service.OrderQueueService;
import com.ticket.service.RocketMQProducerService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static java.lang.Boolean.TRUE;

/**
 * 订单排队服务实现
 * 核心流程：幂等校验 → Redis预扣库存 → MQ入队 → 立即返回requestId
 * 整个过程控制在10ms以内，实现真正的削峰效果
 */
@Service
public class OrderQueueServiceImpl implements OrderQueueService {

    private static final Logger logger = LoggerFactory.getLogger(OrderQueueServiceImpl.class);

    /** 排队结果Redis键前缀 */
    private static final String QUEUE_RESULT_PREFIX = "order:queue:result:";

    /** 排队结果状态 */
    public static final int STATUS_PROCESSING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_EXPIRED = 3;

    /** 结果缓存过期时间（30分钟，足够前端轮询） */
    private static final long RESULT_TTL_MINUTES = 30;

    @Resource
    private TrainOrderGateway trainOrderGateway;

    @Autowired
    private RocketMQProducerService rocketMQProducerService;

    @Resource
    private RedisUtil redisUtil;

    @Override
    public String enqueue(OrderQueueRequest request) {
        // 1. 生成唯一请求ID（幂等键）
        String requestId = IdUtil.fastSimpleUUID();

        // 2. 幂等校验：检查是否已存在相同请求（防止重复入队）
        String idempotentKey = QUEUE_RESULT_PREFIX + requestId;
        Boolean exists = redisUtil.exists(idempotentKey);
        if (TRUE.equals(exists)) {
            logger.warn("重复的排队请求，已返回已有结果: requestId={}", requestId);
            return requestId;
        }

        // 3. 初始化排队结果为PROCESSING
        redisUtil.set(idempotentKey, buildResultJson(STATUS_PROCESSING, null, null), RESULT_TTL_MINUTES, TimeUnit.MINUTES);

        // 4. Redis预扣库存（唯一的同步重量级操作）
        try {
            trainOrderGateway.deductStock(
                    request.getTrainId(),
                    request.getTrainDate(),
                    request.getStartStation(),
                    request.getEndStation(),
                    request.getSeatType(),
                    request.getItems().size()
            );
        } catch (Exception e) {
            // 库存扣减失败，直接更新为失败状态
            logger.error("预扣库存失败，标记请求失败: requestId={}, error={}", requestId, e.getMessage());
            updateResult(requestId, STATUS_FAILED, null, e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

        // 5. 设置requestId到请求对象
        request.setRequestId(requestId);

        // 6. 发送MQ消息到订单队列（异步，不阻塞）
        try {
            rocketMQProducerService.sendOrderQueueMessage(request);
        } catch (RuntimeException mqEx) {
            // MQ 发送失败：仅释放 Redis 预占，不降级同步下单
            logger.error("MQ发送失败，释放预占: requestId={}, error={}", requestId, mqEx.getMessage());
            try {
                trainOrderGateway.rollbackReservation(
                        request.getTrainId(), request.getTrainDate(),
                        request.getStartStation(), request.getEndStation(),
                        request.getSeatType(), request.getItems().size()
                );
            } catch (Exception rollbackEx) {
                logger.error("释放预占失败: requestId={}, error={}", requestId, rollbackEx.getMessage());
            }
            String err = mqEx.getMessage() != null ? mqEx.getMessage() : "消息发送失败";
            updateResult(requestId, STATUS_FAILED, null, err);
            throw mqEx;
        }

        logger.info("下单请求已入队: requestId={}, userId={}, trainId={}, itemCount={}",
                requestId, request.getUserId(), request.getTrainId(), request.getItems().size());

        return requestId;
    }

    @Override
    public int queryStatus(String requestId) {
        if (requestId == null || requestId.isEmpty()) {
            return STATUS_EXPIRED;
        }
        String result = redisUtil.get(QUEUE_RESULT_PREFIX + requestId);
        if (result == null) {
            return STATUS_EXPIRED;
        }
        try {
            JsonNode node = new ObjectMapper().readTree(result);
            return node.get("status").asInt(STATUS_PROCESSING);
        } catch (Exception e) {
            return STATUS_PROCESSING;
        }
    }

    @Override
    public String getOrderNo(String requestId) {
        String result = redisUtil.get(QUEUE_RESULT_PREFIX + requestId);
        if (result == null) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
            return node.has("orderNo") && !node.get("orderNo").isNull() ? node.get("orderNo").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getErrorMessage(String requestId) {
        String result = redisUtil.get(QUEUE_RESULT_PREFIX + requestId);
        if (result == null) {
            return null;
        }
        try {
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
            return node.has("errorMessage") ? node.get("errorMessage").asText(null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 更新排队结果（由Consumer调用）
     */
    @Override
    public void updateResult(String requestId, int status, String orderNo, String errorMessage) {
        String key = QUEUE_RESULT_PREFIX + requestId;
        String json = buildResultJson(status, orderNo, errorMessage);
        redisUtil.set(key, json, RESULT_TTL_MINUTES, TimeUnit.MINUTES);
        if (status == STATUS_SUCCESS) {
            logger.info("排队请求处理成功: requestId={}, orderNo={}", requestId, orderNo);
        } else if (status == STATUS_FAILED) {
            logger.warn("排队请求处理失败: requestId={}, error={}", requestId, errorMessage);
        }
    }

    /**
     * 构建结果JSON字符串
     */
    private String buildResultJson(int status, String orderNo, String errorMessage) {
        StringBuilder sb = new StringBuilder("{\"status\":").append(status);
        if (orderNo != null) {
            sb.append(",\"orderNo\":\"").append(orderNo).append("\"");
        } else {
            sb.append(",\"orderNo\":null");
        }
        if (errorMessage != null) {
            sb.append(",\"errorMessage\":\"").append(escapeJson(errorMessage)).append("\"");
        } else {
            sb.append(",\"errorMessage\":null");
        }
        sb.append(",\"timestamp\":").append(System.currentTimeMillis()).append("}");
        return sb.toString();
    }

    /**
     * JSON特殊字符转义
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}