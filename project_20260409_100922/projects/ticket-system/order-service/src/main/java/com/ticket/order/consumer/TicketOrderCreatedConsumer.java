package com.ticket.order.consumer;

import com.ticket.dto.mq.OrderCreatedEvent;
import com.ticket.enums.CacheKey;
import com.ticket.enums.MQTopics;
import com.ticket.util.MQIdempotentUtil;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单创建后事件：清理用户订单列表缓存（与 backend 同组名，勿与 backend 同时订阅本 Topic）。
 */
@Component
@RocketMQMessageListener(
        topic = MQTopics.TICKET_ORDER,
        consumerGroup = "order-consumer-group",
        messageModel = MessageModel.CLUSTERING
)
public class TicketOrderCreatedConsumer implements RocketMQListener<OrderCreatedEvent> {

    private static final Logger log = LoggerFactory.getLogger(TicketOrderCreatedConsumer.class);

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    public void onMessage(OrderCreatedEvent event) {
        if (event == null || event.getMessageId() == null) {
            log.warn("收到空订单创建事件，跳过");
            return;
        }
        if (idempotentUtil.alreadyConsumed(MQTopics.TICKET_ORDER, event.getMessageId())) {
            return;
        }

        log.info("消费订单创建事件: orderNo={}, userId={}", event.getOrderNo(), event.getUserId());
        redisUtil.delete(String.format(CacheKey.USER_ORDERS, event.getUserId()));
        log.debug("已清除用户订单缓存: userId={}", event.getUserId());
        idempotentUtil.markConsumed(MQTopics.TICKET_ORDER, event.getMessageId());
    }
}
