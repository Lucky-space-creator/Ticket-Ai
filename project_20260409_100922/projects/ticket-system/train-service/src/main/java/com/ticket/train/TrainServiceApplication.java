package com.ticket.train;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 车次服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.ticket")
@EnableDiscoveryClient
@EnableFeignClients
public class TrainServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrainServiceApplication.class, args);
    }
}