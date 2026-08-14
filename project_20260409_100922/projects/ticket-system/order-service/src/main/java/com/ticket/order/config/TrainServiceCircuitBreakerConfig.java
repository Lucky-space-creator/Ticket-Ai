package com.ticket.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 熔断降级配置：保护 order-service 调用 train-service 的同步 Feign 链路。
 *
 * 设计原则：
 * 1. 仅保护「可安全失败」的入队路径（查价 getSeatPrice、预扣 deductStock(sBatch)）。
 * 2. 不保护 rollback / reservationRollback / confirm（补偿与消费侧需保证执行，靠 MQ 重试保证最终一致）。
 * 3. 熔断打开后受保护方法直接抛异常，由 OrderController 的 catch(RuntimeException) 统一转成友好提示，
 *    绝不降级为「绕过 MQ 的同步写单」，以保住异步削峰架构。
 * 4. 使用独立线程池做 ThreadPool 隔离，避免 train-service 抖动时占用 Tomcat 业务线程。
 */
@Configuration
public class TrainServiceCircuitBreakerConfig {

    /** 熔断器实例名，与 application.yml 中 resilience4j.circuitbreaker.instances 对应 */
    public static final String TRAIN_SERVICE_INSTANCE = "trainService";

    /**
     * 专用隔离线程池：承载受保护 Feign 调用，与 Tomcat 线程、MQ 消费线程隔离。
     */
    @Bean("trainServiceCbExecutor")
    public Executor trainServiceCbExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(64);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("train-cb-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new com.ticket.util.MdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.ofDefaults();
    }
}
