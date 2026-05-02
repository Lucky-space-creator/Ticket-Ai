package com.ticket.aichat.client;

import com.ticket.dto.internal.TrainStockCommand;
import com.ticket.entity.Train;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 车次服务 Feign（与 train-service 暴露的内部订单接口对齐，供后续 AI 工具接入）
 */
@FeignClient(name = "train-service", contextId = "aichatTrainClient")
public interface TrainClient {

    @GetMapping("/api/trains/internal/order/by-id/{trainId}")
    Train getTrainDetailById(@PathVariable("trainId") Long trainId);

    @GetMapping("/api/trains/internal/order/seat-price")
    BigDecimal getSeatPrice(@RequestParam("trainId") Long trainId,
                            @RequestParam("trainDate") String trainDate,
                            @RequestParam("startStation") String startStation,
                            @RequestParam("endStation") String endStation,
                            @RequestParam("seatType") Integer seatType);

    @PostMapping(value = "/api/trains/internal/order/deduct-stock", consumes = "application/json")
    void deductStock(@RequestBody TrainStockCommand cmd);

    @PostMapping(value = "/api/trains/internal/order/rollback-stock", consumes = "application/json")
    void rollbackStock(@RequestBody TrainStockCommand cmd);
}
