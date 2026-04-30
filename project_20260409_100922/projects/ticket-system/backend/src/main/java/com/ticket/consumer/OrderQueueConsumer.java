package com.ticket.consumer;

import com.ticket.dto.mq.OrderQueueRequest;
import com.ticket.entity.Order;
import com.ticket.entity.OrderItem;
import com.ticket.entity.Train;
import com.ticket.enums.MQTopics;
import com.ticket.mapper.OrderItemMapper;
import com.ticket.mapper.OrderMapper;
import com.ticket.service.impl.OrderQueueServiceImpl;
import com.ticket.service.StockLockService;
import com.ticket.util.MQIdempotentUtil;
import com.ticket.util.RedisUtil;
import com.ticket.util.SnowflakeIdUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 订单队列消费者（核心削峰组件）
 * 从 order-queue topic 消费消息后，执行DB订单写入
 * 这是整个异步化改造的核心：将同步阻塞的DB写入变为异步消费
 *
 * 消费顺序保证：同一个orderKey的消息会按序消费（MessageQueueSelector）
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopics.ORDER_QUEUE,
        consumerGroup = "order-queue-consumer-group",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 3, // 最多重试3次（库存扣减失败时）
        messageModel = MessageModel.CLUSTERING
)
public class OrderQueueConsumer implements RocketMQListener<OrderQueueRequest> {

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private OrderItemMapper orderItemMapper;

    @Resource
    private StockLockService stockLockService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private OrderQueueServiceImpl queueService;

    @Resource
    private com.ticket.service.TrainService trainService;

    @Override
    public void onMessage(OrderQueueRequest request) {
        if (request == null || request.getRequestId() == null) {
            log.warn("收到空排队请求，跳过");
            return;
        }

        String requestId = request.getRequestId();
        log.info("开始处理排队的下单请求: requestId={}, userId={}, trainId={}",
                requestId, request.getUserId(), request.getTrainId());

        try {
            // 1. 幂等性校验（防止重复消费）
            if (idempotentUtil.isConsumed(MQTopics.ORDER_QUEUE, requestId)) {
                log.info("排队请求已处理过，跳过: requestId={}", requestId);
                return;
            }

            // 2. 执行DB写入（事务内完成）
            Order order = createOrderInDb(request);

            // 3. 更新Redis结果为SUCCESS
            queueService.updateResult(requestId, OrderQueueServiceImpl.STATUS_SUCCESS, order.getOrderNo(), null);

            log.info("排队下单请求处理成功: requestId={}, orderNo={}", requestId, order.getOrderNo());

        } catch (Exception e) {
            log.error("排队下单请求处理失败: requestId={}, error={}", requestId, e.getMessage(), e);

            // 4. 回滚Redis预占库存（防止库存泄露）
            rollbackStock(request);

            // 5. 更新Redis结果为FAILED
            queueService.updateResult(requestId, OrderQueueServiceImpl.STATUS_FAILED, null, e.getMessage());

            // 不抛异常给RocketMQ — 我们已自行处理了回滚和状态更新
            // 如果是可重试错误（如DB临时故障），可以让MQ重试；但库存类错误不应重试
        }
    }

    /**
     * 在数据库中写入订单和明细（核心事务操作）
     * 从原 OrderServiceImpl.createOrder() 中提取出的纯DB写入逻辑
     */
    @Transactional(rollbackFor = Exception.class)
    protected Order createOrderInDb(OrderQueueRequest request) {
        // 1. 计算总价
        BigDecimal totalAmount = request.getItems().stream()
                .map(OrderQueueRequest.PassengerItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. 查询车次信息（获取trainNo等字段）
        Train train = trainService.getById(request.getTrainId());
        if (train == null) {
            throw new RuntimeException("车次信息不存在");
        }

        // 3. 创建订单对象
        Order order = new Order();
        order.setOrderNo(SnowflakeIdUtil.getInstance().nextIdStr());
        order.setUserId(request.getUserId());
        order.setTrainId(request.getTrainId());
        order.setTrainNo(train.getTrainNo());
        order.setTrainDate(LocalDate.parse(request.getTrainDate()));
        order.setStartStation(request.getStartStation());
        order.setEndStation(request.getEndStation());
        order.setSeatType(request.getSeatType());
        order.setTotalAmount(totalAmount);
        order.setStatus(0); // 待支付

        // 4. 写入订单主表
        orderMapper.insert(order);

        // 5. 写入订单明细表
        for (OrderQueueRequest.PassengerItem item : request.getItems()) {
            OrderItem detail = new OrderItem();
            detail.setOrderId(order.getId());
            detail.setPassengerName(item.getPassengerName());
            detail.setIdCard(item.getIdCard()); // 已加密
            detail.setPrice(item.getPrice());
            orderItemMapper.insert(detail);
        }

        return order;
    }

    /**
     * 回滚Redis预占库存
     */
    private void rollbackStock(OrderQueueRequest request) {
        try {
            stockLockService.rollback(
                    request.getTrainId(),
                    request.getTrainDate(),
                    request.getSeatType(),
                    request.getStartStation(),
                    request.getEndStation(),
                    request.getItems().size()
            );
            log.info("已回滚Redis预占库存: requestId={}, trainId={}", request.getRequestId(), request.getTrainId());
        } catch (Exception e) {
            log.error("回滚库存失败: requestId={}, error={}", request.getRequestId(), e.getMessage(), e);
        }
    }
}
