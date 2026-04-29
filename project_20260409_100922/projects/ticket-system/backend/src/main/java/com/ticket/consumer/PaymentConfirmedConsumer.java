package com.ticket.consumer;

import com.ticket.dto.mq.PaymentConfirmedEvent;
import com.ticket.enums.MQTopics;
import com.ticket.service.StockLockService;
import com.ticket.util.MQIdempotentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 支付确认事件消费者
 * 处理 ticket-payment Topic：确认扣减Redis库存（将预占转为实际售出）
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopics.TICKET_PAYMENT,
        consumerGroup = "payment-consumer-group"
)
public class PaymentConfirmedConsumer implements RocketMQListener<PaymentConfirmedEvent> {

    @Resource
    private StockLockService stockLockService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Override
    public void onMessage(PaymentConfirmedEvent event) {
        if (event == null || event.getMessageId() == null) {
            log.warn("收到空支付确认事件，跳过");
            return;
        }

        // 幂等性校验
        if (idempotentUtil.isConsumed(MQTopics.TICKET_PAYMENT, event.getMessageId())) {
            return;
        }

        log.info("消费支付确认事件: orderNo={}, trainId={}, count={}",
                event.getOrderNo(), event.getTrainId(), event.getCount());

        try {
            // 确认扣减Redis库存（将预占转为实际售出）
            String trainDateStr = event.getTrainDate() != null ? event.getTrainDate().toString() : null;
            stockLockService.confirm(
                    event.getTrainId(), trainDateStr,
                    event.getSeatType(), event.getStartStation(),
                    event.getEndStation(), event.getCount());

            log.info("支付库存确认完成: orderNo={}, count={}", event.getOrderNo(), event.getCount());
        } catch (Exception e) {
            log.error("处理支付确认事件失败(将重试): orderNo={}, error={}",
                    event.getOrderNo(), e.getMessage());
            throw e; // 抛出异常触发RocketMQ重试
        }
    }
}
