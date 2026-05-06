package com.ticket.order.integration;

import com.ticket.dto.ValidateRouteSkuRequest;
import com.ticket.dto.ValidatedRouteSkuResponse;
import com.ticket.dto.internal.TrainStockCommand;
import com.ticket.entity.Train;
import com.ticket.order.client.TrainOrderFeignClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单业务访问「车次/库存」能力的唯一入口，内部通过 Feign 调 train-service（对齐 backend 行为，降低与车次实现类的耦合）
 */
@Component
public class TrainOrderGateway {

    @Resource
    private TrainOrderFeignClient trainOrderFeignClient;

    public ValidatedRouteSkuResponse validateRoute(ValidateRouteSkuRequest request) {
        return trainOrderFeignClient.validateRoute(request);
    }

    public Train getTrainById(Long trainId) {
        return trainOrderFeignClient.getById(trainId);
    }

    public BigDecimal getSeatPrice(Long trainId, String trainDate, String startStation, String endStation, Integer seatType) {
        return trainOrderFeignClient.getSeatPrice(trainId, trainDate, startStation, endStation, seatType);
    }

    public void deductStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, int count) {
        trainOrderFeignClient.deductStock(new TrainStockCommand(trainId, trainDate, startStation, endStation, seatType, count));
    }

    public void deductStocksBatch(List<TrainStockCommand> segments) {
        trainOrderFeignClient.deductStocksBatch(segments);
    }

    public void rollbackStocksBatch(List<TrainStockCommand> segments) {
        trainOrderFeignClient.rollbackStocksBatch(segments);
    }

    public void confirmStocksBatch(List<TrainStockCommand> segments) {
        trainOrderFeignClient.confirmStocksBatch(segments);
    }

    public void reservationRollbackBatch(List<TrainStockCommand> segments) {
        trainOrderFeignClient.reservationRollbackBatch(segments);
    }

    public void rollbackStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, int count) {
        trainOrderFeignClient.rollbackStock(new TrainStockCommand(trainId, trainDate, startStation, endStation, seatType, count));
    }

    public void rollbackReservation(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, int count) {
        trainOrderFeignClient.reservationRollback(new TrainStockCommand(trainId, trainDate, startStation, endStation, seatType, count));
    }
}
