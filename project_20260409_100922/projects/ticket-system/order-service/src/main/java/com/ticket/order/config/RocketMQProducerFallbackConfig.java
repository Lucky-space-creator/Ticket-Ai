package com.ticket.order.config;

import com.ticket.dto.mq.OrderCreatedEvent;
import com.ticket.dto.mq.OrderQueueRequest;
import com.ticket.dto.mq.PaymentConfirmedEvent;
import com.ticket.service.RocketMQProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQProducerFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(RocketMQProducerFallbackConfig.class);

    @Bean
    @ConditionalOnMissingBean(RocketMQProducerService.class)
    public RocketMQProducerService rocketMQProducerServiceFallback() {
        return new RocketMQProducerService() {
            @Override
            public void sendOrderQueueMessage(OrderQueueRequest request) {
                log.warn("RocketMQ fallback in use. Queue message ignored: requestId={}", request.getRequestId());
                throw new RuntimeException("消息队列不可用");
            }

            @Override
            public void sendOrderCreatedEvent(OrderCreatedEvent event) {
                log.warn("RocketMQ fallback in use. OrderCreated event ignored: orderNo={}", event.getOrderNo());
            }

            @Override
            public void sendPaymentConfirmedEvent(PaymentConfirmedEvent event) {
                log.warn("RocketMQ fallback in use. PaymentConfirmed event ignored: orderNo={}", event.getOrderNo());
            }
        };
    }
}
