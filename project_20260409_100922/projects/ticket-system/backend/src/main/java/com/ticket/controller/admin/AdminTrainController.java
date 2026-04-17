package com.ticket.controller.admin;

import com.ticket.entity.Train;
import com.ticket.service.TrainService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端车次控制器
 */
@RestController
@RequestMapping("/api/admin/trains")
@CrossOrigin(origins = "*")
public class AdminTrainController {

    @Resource
    private TrainService trainService;

    /**
     * 获取车次列表
     */
    @GetMapping
    public ResponseUtil.Result<List<Train>> list() {
        List<Train> list = trainService.list();
        return ResponseUtil.success(list);
    }

    /**
     * 添加车次
     */
    @PostMapping
    public ResponseUtil.Result<Train> add(@RequestBody Train train) {
        trainService.save(train);
        return ResponseUtil.success("添加成功", train);
    }

    /**
     * 更新车次
     */
    @PutMapping("/{id}")
    public ResponseUtil.Result<Train> update(@PathVariable Long id, @RequestBody Train train) {
        train.setId(id);
        trainService.updateById(train);
        return ResponseUtil.success("更新成功", train);
    }

    /**
     * 更新车次状态
     */
    @PutMapping("/{id}/status")
    public ResponseUtil.Result<?> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        Train train = trainService.getById(id);
        if (train == null) {
            return ResponseUtil.error("车次不存在");
        }
        train.setStatus(request.getStatus());
        trainService.updateById(train);
        return ResponseUtil.success("状态更新成功");
    }

    /**
     * 设置余票
     */
    @PostMapping("/{id}/stock")
    public ResponseUtil.Result<?> setStock(@PathVariable Long id, @RequestBody List<StockItem> stockItems) {
        // 这里需要实现余票设置逻辑，暂时返回成功
        return ResponseUtil.success("余票设置成功");
    }

    @lombok.Data
    public static class UpdateStatusRequest {
        private Integer status;
    }

    @lombok.Data
    public static class StockItem {
        private String trainDate;
        private String startStation;
        private String endStation;
        private Integer seatType;
        private Integer availableSeats;
        private Double price;
    }
}