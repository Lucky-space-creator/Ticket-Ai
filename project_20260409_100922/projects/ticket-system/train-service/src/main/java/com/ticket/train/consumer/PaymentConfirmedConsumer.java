package com.ticket.train.consumer;

import com.ticket.dto.mq.PaymentConfirmedEvent;
import com.ticket.enums.MQTopics;
import com.ticket.service.StockLockService;
import com.ticket.util.MQIdempotentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 支付确认：将 Redis 预占转为实际售出（与 backend 同组名，勿与 backend 同时订阅本 Topic）。
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = MQTopics.TICKET_PAYMENT,
        consumerGroup = "payment-consumer-group",
        messageModel = MessageModel.CLUSTERING
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
        if (idempotentUtil.isConsumed(MQTopics.TICKET_PAYMENT, event.getMessageId())) {
            return;
        }

        log.info("消费支付确认事件: orderNo={}, legs={}, count={}",
                event.getOrderNo(),
                event.getStockLegs() != null ? event.getStockLegs().size() : 0,
                event.getCount());

        try {
            if (event.getStockLegs() != null && !event.getStockLegs().isEmpty()) {
                stockLockService.confirmBatch(event.getStockLegs());
            } else if (event.getTrainId() != null) {
                String trainDateStr = event.getTrainDate() != null ? event.getTrainDate().toString() : null;
                stockLockService.confirm(
                        event.getTrainId(), trainDateStr,
                        event.getSeatType(), event.getStartStation(),
                        event.getEndStation(), event.getCount());
            } else {
                log.warn("支付确认事件无可执行库存段落: orderNo={}", event.getOrderNo());
            }
            log.info("支付库存确认完成: orderNo={}, count={}", event.getOrderNo(), event.getCount());
        } catch (Exception e) {
            log.error("处理支付确认事件失败(将重试): orderNo={}, error={}",
                    event.getOrderNo(), e.getMessage());
            throw e;
        }
    }
}
