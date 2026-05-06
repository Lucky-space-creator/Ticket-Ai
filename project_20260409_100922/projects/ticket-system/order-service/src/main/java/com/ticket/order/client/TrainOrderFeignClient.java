package com.ticket.order.client;

import com.ticket.dto.ValidateRouteSkuRequest;
import com.ticket.dto.ValidatedRouteSkuResponse;
import com.ticket.dto.internal.TrainStockCommand;
import com.ticket.entity.Train;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单域调用车次域的 HTTP 契约（低耦合：不依赖车次服务的 MyBatis Service 接口）
 */
@FeignClient(name = "train-service", contextId = "trainOrderFeignClient", path = "/api/trains/internal/order")
public interface TrainOrderFeignClient {

    @GetMapping("/by-id/{trainId}")
    Train getById(@PathVariable("trainId") Long trainId);

    @GetMapping("/seat-price")
    BigDecimal getSeatPrice(
            @RequestParam("trainId") Long trainId,
            @RequestParam("trainDate") String trainDate,
            @RequestParam("startStation") String startStation,
            @RequestParam("endStation") String endStation,
            @RequestParam("seatType") Integer seatType);

    @PostMapping("/validate-route")
    ValidatedRouteSkuResponse validateRoute(@RequestBody ValidateRouteSkuRequest request);

    @PostMapping("/deduct-stock")
    void deductStock(@RequestBody TrainStockCommand cmd);

    @PostMapping("/deduct-stocks-batch")
    void deductStocksBatch(@RequestBody List<TrainStockCommand> commands);

    @PostMapping("/rollback-stocks-batch")
    void rollbackStocksBatch(@RequestBody List<TrainStockCommand> commands);

    @PostMapping("/confirm-stocks-batch")
    void confirmStocksBatch(@RequestBody List<TrainStockCommand> commands);

    @PostMapping("/reservation/rollback-batch")
    void reservationRollbackBatch(@RequestBody List<TrainStockCommand> commands);

    @PostMapping("/rollback-stock")
    void rollbackStock(@RequestBody TrainStockCommand cmd);

    @PostMapping("/reservation/rollback")
    void reservationRollback(@RequestBody TrainStockCommand cmd);
}
