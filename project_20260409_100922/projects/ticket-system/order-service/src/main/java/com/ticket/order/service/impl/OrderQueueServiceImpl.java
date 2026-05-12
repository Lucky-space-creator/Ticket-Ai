package com.ticket.order.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.checkconfig.RocketMQCheckConfig;
import com.ticket.dto.RouteLeg;
import com.ticket.dto.mq.OrderQueueRequest;
import com.ticket.dto.TrainStockCommands;
import com.ticket.order.integration.TrainOrderGateway;
import com.ticket.order.service.OrderQueueService;
import com.ticket.service.RocketMQProducerService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单排队服务实现
 * 核心流程：幂等键(可选) → Redis预扣库存 → MQ入队 → 立即返回requestId
 */
@Service
public class OrderQueueServiceImpl implements OrderQueueService {

    private static final Logger logger = LoggerFactory.getLogger(OrderQueueServiceImpl.class);

    // 用来序列化和反序列化json
    // 作用：将json字符串转换为json对象，将json对象转换为json字符串
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 排队结果Redis键前缀 */
    private static final String QUEUE_RESULT_PREFIX = "order:queue:result:";

    /** Idempotency-Key → requestId 绑定 */
    private static final String IDEM_PREFIX = "order:queue:idem:";

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

    /**
     * 排队下单
     * @param request           排队请求
     * @param idempotencyKey    客户端幂等键（如 HTTP Idempotency-Key）；可空表示不跨请求去重
     * @return requestId
     */
    @Override
    public String enqueue(OrderQueueRequest request, String idempotencyKey) {
        ensureQueueInvariant(request);

        Long userId = request.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        // 1. 检查幂等键
        if (StringUtils.hasText(idempotencyKey)) {
            String idemStoreKey = idempotencyRedisKey(userId, idempotencyKey);
            String existingRid = redisUtil.get(idemStoreKey);
            // 如果幂等键已存在，则直接返回已存在的requestId
            if (StringUtils.hasText(existingRid)) {
                logger.info("Idempotency-Key 复用已有 requestId={}", existingRid);
                return existingRid;
            }
        }

        // 请求ID
        String requestId = IdUtil.fastSimpleUUID();

        if (StringUtils.hasText(idempotencyKey)) {
            String idemStoreKey = idempotencyRedisKey(userId, idempotencyKey);
            // 如果幂等键不存在，则设置幂等键，并设置过期时间为30分钟
            if (!redisUtil.setIfAbsent(idemStoreKey, requestId, RESULT_TTL_MINUTES, TimeUnit.MINUTES)) {
                // 最终获取到的
                String winner = redisUtil.get(idemStoreKey);
                if (StringUtils.hasText(winner)) {
                    return winner;
                }
            }
        }

        request.setRequestId(requestId);

        String resultKey = QUEUE_RESULT_PREFIX + requestId;
        // 组装排队结果对象，并设置过期时间为30分钟
        redisUtil.set(resultKey, buildResultJson(STATUS_PROCESSING, null, null), RESULT_TTL_MINUTES, TimeUnit.MINUTES);

        int pax = request.getItems().size();
        try {
            List<RouteLeg> legs = request.getLegs();
            if (legs != null && !legs.isEmpty()) {
                // 根据passengerCount和seatType计算总票数
                trainOrderGateway.deductStocksBatch(TrainStockCommands.fromRouteLegs(
                        legs, request.getTrainDate(), request.getSeatType(), pax));
            } else {
                trainOrderGateway.deductStock(
                        request.getTrainId(),
                        request.getTrainDate(),
                        request.getStartStation(),
                        request.getEndStation(),
                        request.getSeatType(),
                        pax);
            }
        } catch (Exception e) {
            logger.error("预扣库存失败，标记请求失败: requestId={}, error={}", requestId, e.getMessage());
            updateResult(requestId, STATUS_FAILED, null, e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

        try {
            rocketMQProducerService.sendOrderQueueMessage(request);
        } catch (RuntimeException mqEx) {
            logger.error("MQ发送失败，释放预占: requestId={}, error={}", requestId, mqEx.getMessage());
            try {
                List<RouteLeg> legs = request.getLegs();
                if (legs != null && !legs.isEmpty()) {
                    trainOrderGateway.reservationRollbackBatch(TrainStockCommands.fromRouteLegs(
                            legs, request.getTrainDate(), request.getSeatType(), pax));
                } else {
                    trainOrderGateway.rollbackReservation(
                            request.getTrainId(), request.getTrainDate(),
                            request.getStartStation(), request.getEndStation(),
                            request.getSeatType(), pax);
                }
            } catch (Exception rollbackEx) {
                logger.error("释放预占失败: requestId={}, error={}", requestId, rollbackEx.getMessage());
            }
            String err = mqEx.getMessage() != null ? mqEx.getMessage() : "消息发送失败";
            updateResult(requestId, STATUS_FAILED, null, err);
            throw mqEx;
        }

        logger.info("下单请求已入队: requestId={}, userId={}, trainId={}, legs={}, itemCount={}",
                requestId, request.getUserId(), request.getTrainId(),
                request.getLegs() != null ? request.getLegs().size() : 0,
                request.getItems().size());

        return requestId;
    }

    private static void ensureQueueInvariant(OrderQueueRequest r) {
        List<RouteLeg> legs = r.getLegs();
        if (legs != null && !legs.isEmpty()) {
            RouteLeg l0 = legs.get(0);
            if (l0 == null || l0.getSegmentId() == null) {
                throw new IllegalArgumentException("行程首节 segmentId 不能为空");
            }
            if (r.getTrainId() == null) {
                r.setTrainId(l0.getSegmentId());
            }
        } else {
            if (r.getTrainId() == null) {
                throw new IllegalArgumentException("车次ID不能为空");
            }
        }
    }

    private static String idempotencyRedisKey(Long userId, String idempotencyKey) {
        return IDEM_PREFIX + userId + ":" + DigestUtil.sha256Hex(idempotencyKey.trim());
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
            JsonNode node = OBJECT_MAPPER.readTree(result);
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
            JsonNode node = OBJECT_MAPPER.readTree(result);
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
            JsonNode node = OBJECT_MAPPER.readTree(result);
            return node.has("errorMessage") ? node.get("errorMessage").asText(null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 更新排队结果（由Consumer调用）；SUCCESS 后不可降级为 FAILED。
     */
    @Override
    public void updateResult(String requestId, int status, String orderNo, String errorMessage) {
        String key = QUEUE_RESULT_PREFIX + requestId;
        String current = redisUtil.get(key);
        if (current == null) {
            logger.warn("updateResult: 无排队记录 requestId={}", requestId);
            return;
        }
        int cur;
        try {
            cur = OBJECT_MAPPER.readTree(current).get("status").asInt(STATUS_PROCESSING);
        } catch (Exception e) {
            cur = STATUS_PROCESSING;
        }
        if (cur == STATUS_SUCCESS) {
            if (status == STATUS_SUCCESS) {
                return;
            }
            logger.warn("updateResult: 已是 SUCCESS，拒绝变更为 {}", status);
            return;
        }
        if (cur == STATUS_FAILED && status == STATUS_FAILED) {
            return;
        }
        if (cur == STATUS_FAILED && status == STATUS_SUCCESS) {
            logger.warn("updateResult: 当前 FAILED，忽略变更为 SUCCESS");
            return;
        }
        redisUtil.set(key, buildResultJson(status, orderNo, errorMessage), RESULT_TTL_MINUTES, TimeUnit.MINUTES);
        if (status == STATUS_SUCCESS) {
            logger.info("排队请求处理成功: requestId={}, orderNo={}", requestId, orderNo);
        } else if (status == STATUS_FAILED) {
            logger.warn("排队请求处理失败: requestId={}, error={}", requestId, errorMessage);
        }
    }

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

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
