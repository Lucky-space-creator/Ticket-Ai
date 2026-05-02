package com.ticket.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * 后台管理限界上下文：按 DDD 分层扫描 {@code com.ticket.admin} 下 interfaces / application / infrastructure。
 * 通用技术组件（JWT、拦截器等）仍来自 {@code com.ticket.util}、{@code com.ticket.config}。
 */
@SpringBootApplication(scanBasePackages = {"com.ticket.admin", "com.ticket.util", "com.ticket.config"})
@MapperScan({"com.ticket.admin.mapper"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ticket.admin.client")
public class AdminServiceApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(AdminServiceApplication.class, args);
        Environment env = context.getEnvironment();
        System.out.println("=== Nacos 加载的数据库配置 ===");
        System.out.println("spring.datasource.url = " + env.getProperty("spring.datasource.url"));
        System.out.println("spring.datasource.username = " + env.getProperty("spring.datasource.username"));
    }
}
