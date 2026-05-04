package com.ticket.aichat;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 验证 aichat-service 在脱离 Nacos/MQ/Redisson 时仍可完成 Spring 上下文装配。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.bootstrap.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false"
        }
)
@ActiveProfiles("test")
class AiChatServiceContextIT {

    private static volatile RedisServer redisServer;
    private static int redisPort;

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    @DynamicPropertySource
    static void registerRedis(DynamicPropertyRegistry registry) {
        if (redisServer == null) {
            synchronized (AiChatServiceContextIT.class) {
                if (redisServer == null) {
                    redisPort = freePort();
                    redisServer = RedisServer.builder().port(redisPort).build();
                    redisServer.start();
                }
            }
        }
        registry.add("spring.data.redis.master.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.master.port", () -> String.valueOf(redisPort));
        registry.add("spring.data.redis.slaves", () -> "127.0.0.1:" + redisPort);
        registry.add("spring.data.redis.database", () -> "0");
        registry.add("spring.data.redis.timeout", () -> "10000");
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("无法分配测试用本地端口", e);
        }
    }

    @AfterAll
    static void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
            redisServer = null;
        }
    }

    @Test
    void contextLoads() {
        // 上下文成功启动即可
    }
}
