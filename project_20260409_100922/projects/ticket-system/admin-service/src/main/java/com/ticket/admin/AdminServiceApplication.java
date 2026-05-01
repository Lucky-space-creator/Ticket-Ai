package com.ticket.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication(scanBasePackages = "com.ticket")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.ticket"})
public class AdminServiceApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AdminServiceApplication.class, args);
        Environment env = context.getEnvironment();
        System.out.println("=== Nacos 加载的数据库配置 ===");
        System.out.println("spring.datasource.url = " + env.getProperty("spring.datasource.url"));
        System.out.println("spring.datasource.username = " + env.getProperty("spring.datasource.username"));
    }
}