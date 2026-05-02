package com.ticket.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类（订单域 + 公共 util + ticket-common 中的 MQ 生产者等 Bean）
 */
@SpringBootApplication(scanBasePackages = {"com.ticket.order", "com.ticket.util", "com.ticket.service"})
@MapperScan("com.ticket.order.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ticket.order.client")
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}