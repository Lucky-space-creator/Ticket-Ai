package com.ticket.order.consumer;

import com.ticket.dto.RouteLeg;
import com.ticket.dto.mq.OrderQueueRequest;
import com.ticket.entity.Order;
import com.ticket.enums.MQTopics;
import com.ticket.order.integration.TrainOrderGateway;
import com.ticket.dto.TrainStockCommands;
import com.ticket.order.service.OrderQueueDbWriter;
import com.ticket.order.service.OrderQueueService;
import com.ticket.order.service.impl.OrderQueueServiceImpl;
import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单排队队列消费者：微服务运行平面内唯一消费 {@link MQTopics#ORDER_QUEUE} 的组件（勿与 backend 同时订阅同组）。
 */
@Component
@RocketMQMessageListener(
        topic = MQTopics.ORDER_QUEUE,
        consumerGroup = "order-queue-consumer-group",
        consumeMode = ConsumeMode.CONCURRENTLY,
        maxReconsumeTimes = 3,
        messageModel = MessageModel.CLUSTERING
)
public class OrderQueueConsumer implements RocketMQListener<OrderQueueRequest> {

    private static final Logger log = LoggerFactory.getLogger(OrderQueueConsumer.class);

    @Resource
    private OrderQueueDbWriter orderQueueDbWriter;

    @Resource
    private TrainOrderGateway trainOrderGateway;

    @Resource
    private OrderQueueService orderQueueService;

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
            if (orderQueueService.queryStatus(requestId) == OrderQueueServiceImpl.STATUS_SUCCESS) {
                log.info("排队结果已为成功，跳过重复消费: requestId={}", requestId);
                return;
            }

            Order order = orderQueueDbWriter.createOrderFromQueue(request);

            orderQueueService.updateResult(requestId, OrderQueueServiceImpl.STATUS_SUCCESS, order.getOrderNo(), null);

            log.info("排队下单请求处理成功: requestId={}, orderNo={}", requestId, order.getOrderNo());

        } catch (Exception e) {
            log.error("排队下单请求处理失败: requestId={}, error={}", requestId, e.getMessage(), e);
            releasePrelockedStock(request);
            orderQueueService.updateResult(requestId, OrderQueueServiceImpl.STATUS_FAILED, null, e.getMessage());
        }
    }

    /** 异步写单失败：仅释放 Redis 预占（与 enqueue 时 deduct 一致，勿调 rollbackStock 以免误增 MySQL 余票） */
    private void releasePrelockedStock(OrderQueueRequest request) {
        try {
            int pax = request.getItems().size();
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
            log.info("已释放Redis预占: requestId={}, trainId={}", request.getRequestId(), request.getTrainId());
        } catch (Exception e) {
            log.error("释放预占失败: requestId={}, error={}", request.getRequestId(), e.getMessage(), e);
        }
    }
}
