package com.ticket.train.controller;

import com.ticket.train.service.TrainStockCacheInvalidateService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部缓存失效（供 admin-service 等编排调用）。
 */
@RestController
@RequestMapping("/api/trains/internal/cache")
@CrossOrigin(origins = "*")
public class TrainInternalCacheController {

    @Resource
    private TrainStockCacheInvalidateService trainStockCacheInvalidateService;

    @PostMapping("/invalidate-ticket-stock/{id}")
    public void invalidateTicketStock(@PathVariable("id") Long ticketStockId) {
        trainStockCacheInvalidateService.invalidateByStockId(ticketStockId);
    }
}
