package com.ticket.gateway.config;

import io.lettuce.core.ReadFrom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Redis 主从模式配置类（仅 prod 生效）。
 * <p>
 * dev 环境使用 Spring Boot 自动配置的单节点连接，无需额外配置。
 */
@Slf4j
@Configuration
@Profile("prod")
public class RedisMasterSlaveConfig {

    @Value("${spring.data.redis.master.host}")
    private String masterHost;

    @Value("${spring.data.redis.master.port}")
    private int masterPort;

    @Value("${spring.data.redis.slaves:}")
    private String slaves;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:10000}")
    private int timeout;

    @Bean
    @Primary
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
        RedisStaticMasterReplicaConfiguration masterReplicaConfig =
                new RedisStaticMasterReplicaConfiguration(masterHost, masterPort);
        masterReplicaConfig.setDatabase(database);

        parseSlaves().forEach(slave -> {
            String[] hostPort = slave.split(":");
            if (hostPort.length == 2) {
                masterReplicaConfig.addNode(hostPort[0], Integer.parseInt(hostPort[1]));
            }
        });

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .readFrom(ReadFrom.REPLICA_PREFERRED)
                .commandTimeout(java.time.Duration.ofMillis(timeout))
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(masterReplicaConfig, clientConfig);
        factory.afterPropertiesSet();

        log.info("=== 初始化 Gateway Redis 主从模式 ===");
        log.info("主节点: {}:{}", masterHost, masterPort);
        log.info("从节点: {}", slaves);

        return factory;
    }

    private List<String> parseSlaves() {
        if (!StringUtils.hasText(slaves)) {
            return List.of();
        }
        return Arrays.asList(slaves.split(","));
    }
}
