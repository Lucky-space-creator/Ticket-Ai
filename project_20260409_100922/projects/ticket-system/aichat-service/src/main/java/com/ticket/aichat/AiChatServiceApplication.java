package com.ticket.aichat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 智能客服服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.ticket")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.ticket"})
public class AiChatServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiChatServiceApplication.class, args);
    }
}