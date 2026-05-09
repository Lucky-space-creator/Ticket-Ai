package com.ticket.train;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 车次服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.ticket")
@EnableDiscoveryClient  // 开启服务注册与发现
@EnableFeignClients(basePackages = "com.ticket.train.client")
@EnableScheduling  // 开启定时任务
public class TrainServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrainServiceApplication.class, args);
    }
}