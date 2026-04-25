package com.ticket.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Redisson 配置类 - 主从模式
 * 用于提供分布式锁和原子操作支持
 * 配置规则：写用主节点，读用从节点
 * 节点端口配置：主节点 6379，从节点 6380,6381（可扩展更多从节点）
 */
@Configuration
public class RedissonConfig {

    /**
     * 主节点主机，默认 localhost
     */
    @Value("${spring.data.redis.master.host:localhost}")
    private String masterHost;

    /**
     * 主节点端口，默认 6380
     */
    @Value("${spring.data.redis.master.port:6380}")
    private int masterPort;

    /**
     * 从节点配置字符串，格式：host:port,host:port
     * 例如：localhost:6381,localhost:6382
     */
    @Value("${spring.data.redis.slaves:localhost:6381}")
    private String slaves;

    /**
     * 数据库索引，默认 0
     */
    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * 连接超时时间（毫秒），默认 10000
     */
    @Value("${spring.data.redis.timeout:10000}")
    private int redisTimeout;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 构建主节点地址
        String masterAddress = String.format("redis://%s:%d", masterHost, masterPort);

        // 解析从节点地址
        List<String> slaveAddresses = parseSlaveAddresses();

        // 使用主从模式配置
        config.useMasterSlaveServers()
                .setMasterAddress(masterAddress)
                .addSlaveAddress(slaveAddresses.toArray(new String[0]))
                .setDatabase(redisDatabase)
                .setTimeout(redisTimeout)
                .setMasterConnectionPoolSize(64)      // 主节点连接池大小
                .setMasterConnectionMinimumIdleSize(8) // 主节点最小空闲连接数
                .setSlaveConnectionPoolSize(64)       // 从节点连接池大小
                .setSlaveConnectionMinimumIdleSize(8); // 从节点最小空闲连接数

        return Redisson.create(config);
    }

    /**
     * 解析从节点配置字符串，生成 Redis 地址列表
     *
     * @return 从节点地址列表（格式：redis://host:port）
     */
    private List<String> parseSlaveAddresses() {
        if (!StringUtils.hasText(slaves)) {
            return List.of("redis://localhost:6381");
        }
        return Arrays.stream(slaves.split(","))
                .map(slave -> {
                    String[] hostPort = slave.split(":");
                    if (hostPort.length == 2) {
                        String host = hostPort[0];
                        int port = Integer.parseInt(hostPort[1]);
                        return String.format("redis://%s:%d", host, port);
                    }
                    return slave;
                })
                .toList();
    }
}