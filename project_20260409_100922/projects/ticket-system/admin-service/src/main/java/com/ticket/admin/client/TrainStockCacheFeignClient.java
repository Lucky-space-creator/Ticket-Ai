package com.ticket.admin.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "train-service", contextId = "trainStockCacheFeignClient")
public interface TrainStockCacheFeignClient {

    /**
     * 删除车次库存缓存
     *
     * @param ticketStockId 车次库存 ID
     */
    @PostMapping("/api/trains/internal/cache/invalidate-ticket-stock/{id}")
    void invalidateTicketStock(@PathVariable("id") Long ticketStockId);
}
