package com.ticket.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "train-service", contextId = "trainStockCacheFeignClient")
public interface TrainStockCacheFeignClient {

    @PostMapping("/api/trains/internal/cache/invalidate-ticket-stock/{id}")
    void invalidateTicketStock(@PathVariable("id") Long ticketStockId);
}
