package com.ticket.aichat.client;

import com.ticket.entity.Train;
import com.ticket.entity.TicketStock;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

/**
 * 车次服务 Feign 客户端
 */
@FeignClient(name = "train-service", path = "/api/train")
public interface TrainClient {

    /**
     * 搜索车次
     */
    @GetMapping("/search")
    List<Train> searchTrains(@RequestParam("startStation") String startStation,
                             @RequestParam("endStation") String endStation,
                             @RequestParam("trainDate") String trainDate);

    /**
     * 获取车次详情（按车次号）
     */
    @GetMapping("/detail")
    Train getTrainDetail(@RequestParam("trainNo") String trainNo);

    /**
     * 获取车次详情（按车次ID）- 内部使用
     */
    @GetMapping("/detailById")
    Train getTrainDetailById(@RequestParam("trainId") Long trainId);

    /**
     * 获取余票信息
     */
    @GetMapping("/ticketStocks")
    List<TicketStock> getTicketStocks(@RequestParam("trainId") Long trainId,
                                      @RequestParam("trainDate") String trainDate);

    /**
     * 获取指定席别的票价
     */
    @GetMapping("/seatPrice")
    BigDecimal getSeatPrice(@RequestParam("trainId") Long trainId,
                            @RequestParam("trainDate") String trainDate,
                            @RequestParam("startStation") String startStation,
                            @RequestParam("endStation") String endStation,
                            @RequestParam("seatType") Integer seatType);

    /**
     * 检查并扣减库存
     */
    @GetMapping("/deductStock")
    boolean deductStock(@RequestParam("trainId") Long trainId,
                        @RequestParam("trainDate") String trainDate,
                        @RequestParam("startStation") String startStation,
                        @RequestParam("endStation") String endStation,
                        @RequestParam("seatType") Integer seatType,
                        @RequestParam("count") Integer count);

    /**
     * 回滚库存
     */
    @GetMapping("/rollbackStock")
    boolean rollbackStock(@RequestParam("trainId") Long trainId,
                          @RequestParam("trainDate") String trainDate,
                          @RequestParam("startStation") String startStation,
                          @RequestParam("endStation") String endStation,
                          @RequestParam("seatType") Integer seatType,
                          @RequestParam("count") Integer count);
}