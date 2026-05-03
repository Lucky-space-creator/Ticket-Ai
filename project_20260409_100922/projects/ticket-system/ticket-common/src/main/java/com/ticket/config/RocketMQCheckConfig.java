package com.ticket.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;

/**
 * RocketMQ 启动探测：在 {@link RocketMQTemplate} 初始化完成后立刻拉取路由，
 * 确保 NameServer 可达后再装配依赖 MQ 的业务 Bean。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(RocketMQTemplate.class)
public class RocketMQCheckConfig {

    /** Broker 内置 topic，用于探测路由，无需预建业务 topic */
    private static final String PROBE_TOPIC = "TBW102";

    @Bean
    public RocketMQTemplateStartupProbe rocketMQTemplateStartupProbe(
            @Value("${rocketmq.enabled:true}") boolean enabled,
            @Value("${rocketmq.detect-enabled:true}") boolean detectEnabled,
            @Value("${rocketmq.fail-fast:true}") boolean failFast) {
        return new RocketMQTemplateStartupProbe(enabled, detectEnabled, failFast);
    }

    @Slf4j
    public static final class RocketMQTemplateStartupProbe implements BeanPostProcessor, Ordered {

        private final boolean enabled;
        private final boolean detectEnabled;
        private final boolean failFast;

        public RocketMQTemplateStartupProbe(boolean enabled, boolean detectEnabled, boolean failFast) {
            this.enabled = enabled;
            this.detectEnabled = detectEnabled;
            this.failFast = failFast;
        }

        @Override
        public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
            if (!(bean instanceof RocketMQTemplate template)) {
                return bean;
            }
            if (!enabled) {
                log.info("RocketMQ 已全局禁用(rocketmq.enabled=false)，跳过启动探测");
                return bean;
            }
            if (!detectEnabled) {
                log.info("RocketMQ 启动探测已关闭(rocketmq.detect-enabled=false)，跳过");
                return bean;
            }
            try {
                template.getProducer().fetchPublishMessageQueues(PROBE_TOPIC);
                log.info("RocketMQ NameServer 可达: beanName={}, probeTopic={}, producerGroup={}",
                        beanName, PROBE_TOPIC, template.getProducer().getProducerGroup());
            } catch (Exception e) {
                if (failFast) {
                    throw new BeanCreationException(beanName,
                            "RocketMQ NameServer 不可达或路由拉取失败，启动终止（rocketmq.fail-fast=true）", e);
                }
                log.warn("RocketMQ 启动探测失败但 rocketmq.fail-fast=false，继续启动: {}", e.toString());
            }
            return bean;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
