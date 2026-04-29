package com.ticket.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "rocketmq", name = "name-server")
public class RocketMQCheckConfig {

    @Value("${rocketmq.name-server}")
    private String nameServer;

    @Value("${rocketmq.producer.test-topic}")
    private String testTopic;

    @Value("${rocketmq.check.enabled}")
    private boolean checkEnabled;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @PostConstruct
    public void checkNameServerConfig() {
        if (!StringUtils.hasText(nameServer)) {
            throw new IllegalStateException("RocketMQ name-server 配置不能为空！");
        }
        log.info("RocketMQ name-server 配置为: {}", nameServer);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void testRocketMQConnection() {
        if (!checkEnabled) {
            log.info("RocketMQ 启动测试已禁用");
            return;
        }
        if (rocketMQTemplate == null) {
            log.warn("未检测到 RocketMQTemplate Bean，跳过连接测试。请确认依赖已引入。");
            return;
        }

        try {
            String testMessage = "RocketMQ connectivity test from " + System.currentTimeMillis();
            // 使用 syncSend，更简洁
            SendResult sendResult = rocketMQTemplate.syncSend(testTopic, testMessage, 3000);
            if (sendResult != null && "SEND_OK".equals(sendResult.getSendStatus().name())) {
                log.info("✅ RocketMQ 连接校验成功！NameServer: {}, Topic: {}, MsgId: {}",
                        nameServer, testTopic, sendResult.getMsgId());
            } else {
                throw new RuntimeException("发送测试消息失败，状态：" + sendResult);
            }
        } catch (Exception e) {
            log.error("❌ RocketMQ 连接校验失败！NameServer: {}", nameServer, e);
            throw new RuntimeException("RocketMQ 连接异常，应用启动中止", e);
        }
    }
}