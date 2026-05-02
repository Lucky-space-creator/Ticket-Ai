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
 * 作用: 提供内部订单服务接口,为 AI 模型提供车次信息的工具
 */
@FeignClient(name = "train-service", contextId = "aichatTrainClient")
public interface TrainClient {

    /**
     * 获取车次详情
     * @param trainId 车次ID
     * @return  车
     */
    @GetMapping("/api/trains/internal/order/by-id/{trainId}")
    Train getTrainDetailById(@PathVariable("trainId") Long trainId);

    /**
     * 获取座位价格
     * @param trainId 车次ID
     * @param trainDate 乘车日期
     * @param startStation 出发站
     * @param endStation 到达站
     * @param seatType 座位类型
     * @return 座位价格
     */
    @GetMapping("/api/trains/internal/order/seat-price")
    BigDecimal getSeatPrice(@RequestParam("trainId") Long trainId,
                            @RequestParam("trainDate") String trainDate,
                            @RequestParam("startStation") String startStation,
                            @RequestParam("endStation") String endStation,
                            @RequestParam("seatType") Integer seatType);

    /**
     * 扣减库存
     * @param cmd 扣减库存命令
     */
    @PostMapping(value = "/api/trains/internal/order/deduct-stock", consumes = "application/json")
    void deductStock(@RequestBody TrainStockCommand cmd);

    /**
     * 扣减库存回滚
     * @param cmd 扣减库存回滚命令
     */
    @PostMapping(value = "/api/trains/internal/order/rollback-stock", consumes = "application/json")
    void rollbackStock(@RequestBody TrainStockCommand cmd);
}
