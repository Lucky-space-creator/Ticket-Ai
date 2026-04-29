package com.ticket.consumer;

import com.ticket.dto.mq.OrderCreatedEvent;
import com.ticket.dto.mq.PaymentConfirmedEvent;
import com.ticket.enums.CacheKey;
import com.ticket.enums.MQTopics;
import com.ticket.service.StockLockService;
import com.ticket.util.MQIdempotentUtil;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单事件消费者
 * 处理 ticket-order（创建后处理）和 ticket-payment（支付确认）两个Topic
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopics.TICKET_ORDER,
        consumerGroup = "order-consumer-group"
)
public class OrderCreatedConsumer implements RocketMQListener<OrderCreatedEvent> {

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private StockLockService stockLockService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    public void onMessage(OrderCreatedEvent event) {
        if (event == null || event.getMessageId() == null) {
            log.warn("收到空订单创建事件，跳过");
            return;
        }
        // 幂等性校验
        if (idempotentUtil.isConsumed(MQTopics.TICKET_ORDER, event.getMessageId())) {
            return;
        }

        log.info("消费订单创建事件: orderNo={}, userId={}", event.getOrderNo(), event.getUserId());

        // 清除用户订单缓存
        redisUtil.delete(String.format(CacheKey.USER_ORDERS, event.getUserId()));
        log.debug("已清除用户订单缓存: userId={}", event.getUserId());
    }
}
