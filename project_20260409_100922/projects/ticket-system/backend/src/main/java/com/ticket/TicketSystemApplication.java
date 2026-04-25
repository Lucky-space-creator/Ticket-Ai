package com.ticket;

import org.jetbrains.annotations.TestOnly;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 12306购票系统启动类
 */
@SpringBootApplication
@MapperScan("com.ticket.mapper")
@EnableScheduling
public class TicketSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketSystemApplication.class, args);
        System.out.println("========================================");
        System.out.println("12306购票系统启动成功！");
        System.out.println("访问地址: http://localhost:8080");
        System.out.println("========================================");
    }
}
