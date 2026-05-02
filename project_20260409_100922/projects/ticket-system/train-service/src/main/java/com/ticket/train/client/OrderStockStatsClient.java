package com.ticket.train.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "order-service", contextId = "orderStockStatsClient")
public interface OrderStockStatsClient {

    @GetMapping("/api/internal/orders/stock-stats/valid-item-count")
    int countValidOrderItems(
            @RequestParam("trainId") Long trainId,
            @RequestParam("trainDate") String trainDate,
            @RequestParam("seatType") Integer seatType,
            @RequestParam("startStation") String startStation,
            @RequestParam("endStation") String endStation
    );
}
