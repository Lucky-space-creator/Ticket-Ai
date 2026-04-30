package com.ticket.train.controller;

import com.ticket.entity.TicketStock;
import com.ticket.enums.BusinessStatus;
import com.ticket.entity.Train;
import com.ticket.train.service.TrainService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 车次控制器
 */
@RestController
@RequestMapping("/api/trains")
@CrossOrigin(origins = "*")
public class TrainController {

    @Resource
    private TrainService trainService;

    /**
     * 查询车次
     * @param startStation 出发站
     * @param endStation 终点站
     * @param trainDate 日期
     * @return 车次列表
     */
    @GetMapping("/search")
    public ResponseUtil.Result<?> searchTrains(
            @RequestParam String startStation,
            @RequestParam String endStation,
            @RequestParam String trainDate
    ) {
        try {
            List<Train> trains = trainService.searchTrains(startStation, endStation, trainDate);

            // 添加余票信息
            List<Map<String, Object>> result = new ArrayList<>();
            for (Train train : trains) {
                Map<String, Object> trainInfo = new HashMap<>();
                trainInfo.put("id", train.getId());
                trainInfo.put("trainNo", train.getTrainNo());
                trainInfo.put("trainType", train.getTrainType());
                trainInfo.put("trainTypeName", getTrainTypeName(train.getTrainType()));
                trainInfo.put("startStation", train.getStartStation());
                trainInfo.put("endStation", train.getEndStation());
                trainInfo.put("startTime", train.getStartTime());
                trainInfo.put("endTime", train.getEndTime());

                // 获取余票信息
                List<TicketStock> stocks = trainService.getTicketStocks(train.getId(), trainDate);
                trainInfo.put("stocks", stocks);

                result.add(trainInfo);
            }

            return ResponseUtil.success(result);
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 获取车次详情
     */
    @GetMapping("/{id}")
    public ResponseUtil.Result<?> getTrainDetail(@PathVariable Long id) {
        try {
            Train train = trainService.getTrainDetailById(id);

            if (train == null) {
                return ResponseUtil.error("车次不存在");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("id", train.getId());
            data.put("trainNo", train.getTrainNo());
            data.put("trainType", train.getTrainType());
            data.put("trainTypeName", getTrainTypeName(train.getTrainType()));
            data.put("startStation", train.getStartStation());
            data.put("endStation", train.getEndStation());
            data.put("startTime", train.getStartTime());
            data.put("endTime", train.getEndTime());
            data.put("status", train.getStatus());

            return ResponseUtil.success(data);
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 获取车次类型名称
     */
    private String getTrainTypeName(Integer trainType) {
        if (trainType == null) {
            return "未知";
        }
        //用if-else
        if (trainType.equals(BusinessStatus.TRAIN_TYPE_GAOTIE)) {
            return "高铁";
        }
        else if (trainType.equals(BusinessStatus.TRAIN_TYPE_DONGCHE)) {
            return "动车";
        }
        else if (trainType.equals(BusinessStatus.TRAIN_TYPE_PUKUAI)) {
            return "普快";
        }
        else {
            return "未知";
        }
    }
}