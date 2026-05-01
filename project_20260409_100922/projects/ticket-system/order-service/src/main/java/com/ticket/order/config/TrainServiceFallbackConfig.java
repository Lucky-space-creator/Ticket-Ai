package com.ticket.order.config;

import com.ticket.service.TrainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;
/**
 * Fallback TrainService bean for standalone startup.
 * This keeps order-service bootable when no local TrainService implementation is present.
 */
@Configuration
public class TrainServiceFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(TrainServiceFallbackConfig.class);

    @Bean
    @ConditionalOnMissingBean(TrainService.class)
    public TrainService trainServiceFallback() {
        return (TrainService) Proxy.newProxyInstance(
                TrainService.class.getClassLoader(),
                new Class[]{TrainService.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    switch (name) {
                        case "getById":
                        case "getTrainDetailById":
                            log.warn("TrainService fallback used: {}. Returning null.", name);
                            return null;
                        case "getSeatPrice":
                            throw new RuntimeException("train-service remote call is not configured");
                        case "deductStock":
                        case "rollbackStock":
                            throw new RuntimeException("train-service stock operation is not configured");
                        case "searchTrains":
                        case "getTicketStocks":
                            return java.util.Collections.emptyList();
                        case "getTrainDetail":
                            return null;
                        case "initStockToRedis":
                            return null;
                        case "toString":
                            return "TrainServiceFallbackProxy";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            // methods from MyBatis IService
                            throw new UnsupportedOperationException("Unsupported TrainService method: " + name);
                    }
                });
    }
}
