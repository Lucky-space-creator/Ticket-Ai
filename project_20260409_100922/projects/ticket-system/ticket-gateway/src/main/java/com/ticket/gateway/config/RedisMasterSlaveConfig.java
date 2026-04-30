package com.ticket.gateway.config;

import io.lettuce.core.ReadFrom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Redis 主从模式配置类 - 反应式版本
 * 实现一主多从架构：写操作使用主节点，读操作使用从节点（负载均衡）
 * 配置规则：
 *   - 主节点：端口 6379（写操作）
 *   - 从节点：端口 6380,6381（读操作），可扩展更多从节点
 * 使用 Lettuce 客户端实现读写分离，配置 ReadFrom.REPLICA_PREFERRED 优先从节点读取
 */
@Slf4j
@Configuration
public class RedisMasterSlaveConfig {

    /**
     * 主节点主机，默认 localhost
     */
    @Value("${spring.data.redis.master.host:localhost}")
    private String masterHost;

    /**
     * 主节点端口，默认 6379
     */
    @Value("${spring.data.redis.master.port:6379}")
    private int masterPort;

    /**
     * 从节点配置字符串，格式：host:port,host:port
     * 例如：localhost:6380,localhost:6381
     */
    @Value("${spring.data.redis.slaves:localhost:6380,localhost:6381}")
    private String slaves;

    /**
     * 数据库索引，默认 0
     */
    @Value("${spring.data.redis.database:0}")
    private int database;

    /**
     * 连接超时时间（毫秒），默认 10000
     */
    @Value("${spring.data.redis.timeout:10000}")
    private int timeout;

    /**
     * 创建主从模式的 Redis 连接工厂 - 反应式版本
     * 标记为 @Primary，以覆盖默认的单节点连接工厂
     *
     * @return 配置了主从读写分离的 LettuceConnectionFactory
     */
    @Bean
    @Primary
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
        // 1. 配置主节点
        RedisStaticMasterReplicaConfiguration masterReplicaConfig = 
                new RedisStaticMasterReplicaConfiguration(masterHost, masterPort);
        masterReplicaConfig.setDatabase(database);

        // 2. 添加从节点
        parseSlaves().forEach(slave -> {
            String[] hostPort = slave.split(":");
            if (hostPort.length == 2) {
                String host = hostPort[0];
                int port = Integer.parseInt(hostPort[1]);
                masterReplicaConfig.addNode(host, port);
            }
        });

        // 3. 配置 Lettuce 客户端，设置读写分离策略
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .readFrom(ReadFrom.REPLICA_PREFERRED)  // 优先从节点读取
                .commandTimeout(java.time.Duration.ofMillis(timeout))
                .build();

        // 4. 创建连接工厂
        LettuceConnectionFactory factory = new LettuceConnectionFactory(masterReplicaConfig, clientConfig);
        factory.afterPropertiesSet();

        log.info("=== 初始化Gateway Redis主从模式配置 ===");
        log.info("redis主节点配置信息: {}:{}", masterHost, masterPort);
        log.info("redis从节点配置信息: {}", slaves);

        return factory;
    }

    /**
     * 解析从节点配置字符串
     *
     * @return 从节点列表（host:port 格式）
     */
    private List<String> parseSlaves() {
        // 如果没有配置从节点，则使用默认配置
        if (!StringUtils.hasText(slaves)) {
            return List.of(String.format("%s:%d", masterHost, masterPort));
        }
        return Arrays.asList(slaves.split(","));
    }
}