package com.ticket.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 健康检查配置类
 * 在应用启动时检查RocketMQ连接状态，自动禁用问题组件
 */
@Slf4j
@Configuration
@ConditionalOnBean(RocketMQTemplate.class)
public class RocketMQCheckConfig {

    @Value("${rocketmq.enabled:true}")
    private boolean enabled;

    @Value("${rocketmq.detect-enabled:true}")
    private boolean detectEnabled;

    private final RocketMQTemplate rocketMQTemplate;

    public RocketMQCheckConfig(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 启动时检测RocketMQ连接
     */
    @PostConstruct
    public void detectRocketMQ() {
        if (!enabled) {
            log.info("RocketMQ功能已全局禁用");
            return;
        }

        if (!detectEnabled) {
            log.info("RocketMQ检测功能已禁用，跳过连接测试");
            return;
        }

        log.info("开始检测RocketMQ连接状态...");

        try {
            // 尝试发送一个测试消息
            String testTopic = "health-check-topic-" + System.currentTimeMillis();
            rocketMQTemplate.syncSend(testTopic, "health-check");

            log.info("✅ RocketMQ连接正常，消息队列功能已启用");
        } catch (Exception e) {
            log.warn("❌ RocketMQ连接失败，将禁用消息队列功能，错误信息: {}", e.getMessage());

            // 禁用RocketMQ，后续业务代码会检查enabled标志
            enabled = false;
        }
    }

    /**
     * 判断RocketMQ是否可用
     */
    public boolean isEnabled() {
        return enabled;
    }
}