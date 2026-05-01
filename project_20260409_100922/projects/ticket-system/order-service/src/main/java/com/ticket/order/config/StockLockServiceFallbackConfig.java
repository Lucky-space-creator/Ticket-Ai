package com.ticket.order.config;

import com.ticket.service.StockLockService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StockLockServiceFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(StockLockService.class)
    public StockLockService stockLockServiceFallback() {
        return new StockLockService() {
            @Override
            public boolean tryDeduct(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
                return false;
            }

            @Override
            public void rollback(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
                // no-op fallback
            }

            @Override
            public void confirm(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count) {
                // no-op fallback
            }

            @Override
            public void initStock(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock, boolean force) {
                // no-op fallback
            }
        };
    }
}
