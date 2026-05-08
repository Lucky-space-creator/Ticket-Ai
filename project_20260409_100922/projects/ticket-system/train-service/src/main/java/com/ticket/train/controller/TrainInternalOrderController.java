package com.ticket.train.controller;

import com.ticket.dto.ValidateRouteSkuRequest;
import com.ticket.dto.ValidatedRouteSkuResponse;
import com.ticket.dto.internal.TrainStockCommand;
import com.ticket.entity.Train;
import com.ticket.service.StockLockService;
import com.ticket.train.service.TrainRouteSkuService;
import com.ticket.train.service.TrainService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供订单服务调用的车次/库存内部 API（与 backend 中 TrainServiceImpl + StockLockService 协作方式一致）
 */
@RestController
@RequestMapping("/api/trains/internal/order")
@CrossOrigin(origins = "*")
public class TrainInternalOrderController {

    @Resource
    private TrainService trainService;

    @Resource
    private StockLockService stockLockService;

    @Resource
    private TrainRouteSkuService trainRouteSkuService;

    /**
     * 验证车次库存
     * @param request 车次库存验证请求
     * @return 车次库存验证响应
     */
    @PostMapping("/validate-route")
    public ValidatedRouteSkuResponse validateRoute(@RequestBody ValidateRouteSkuRequest request) {
        return trainRouteSkuService.validateSku(request);
    }

    @GetMapping("/by-id/{trainId}")
    public Train getById(@PathVariable("trainId") Long trainId) {
        return trainService.getById(trainId);
    }

    @GetMapping("/seat-price")
    public BigDecimal getSeatPrice(
            @RequestParam("trainId") Long trainId,
            @RequestParam("trainDate") String trainDate,
            @RequestParam("startStation") String startStation,
            @RequestParam("endStation") String endStation,
            @RequestParam("seatType") Integer seatType) {
        return trainService.getSeatPrice(trainId, trainDate, startStation, endStation, seatType);
    }

    @PostMapping("/deduct-stock")
    public void deductStock(@RequestBody TrainStockCommand cmd) {
        trainService.deductStock(
                cmd.getTrainId(),
                cmd.getTrainDate(),
                cmd.getStartStation(),
                cmd.getEndStation(),
                cmd.getSeatType(),
                cmd.getCount());
    }

    @PostMapping("/deduct-stocks-batch")
    public void deductStocksBatch(@RequestBody List<TrainStockCommand> commands) {
        trainService.deductStocksBatch(commands);
    }

    @PostMapping("/rollback-stocks-batch")
    public void rollbackStocksBatch(@RequestBody List<TrainStockCommand> commands) {
        trainService.rollbackStocksBatch(commands);
    }

    @PostMapping("/confirm-stocks-batch")
    public void confirmStocksBatch(@RequestBody List<TrainStockCommand> commands) {
        stockLockService.confirmBatch(commands);
    }

    @PostMapping("/reservation/rollback-batch")
    public void reservationRollbackBatch(@RequestBody List<TrainStockCommand> commands) {
        stockLockService.rollbackBatch(commands);
    }

    @PostMapping("/rollback-stock")
    public void rollbackStock(@RequestBody TrainStockCommand cmd) {
        trainService.rollbackStock(
                cmd.getTrainId(),
                cmd.getTrainDate(),
                cmd.getStartStation(),
                cmd.getEndStation(),
                cmd.getSeatType(),
                cmd.getCount());
    }

    /**
     * 下单失败时仅回滚 Redis 预占（与 backend OrderServiceImpl 中 stockLockService.rollback 一致）
     */
    @PostMapping("/reservation/rollback")
    public void reservationRollback(@RequestBody TrainStockCommand cmd) {
        stockLockService.rollback(
                cmd.getTrainId(),
                cmd.getTrainDate(),
                cmd.getSeatType(),
                cmd.getStartStation(),
                cmd.getEndStation(),
                cmd.getCount());
    }
}
