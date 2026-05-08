package com.ticket.admin.controller;

import com.ticket.admin.service.AdminTrainService;
import com.ticket.annotation.OperationLog;
import com.ticket.entity.Train;
import com.ticket.operationlog.Module;
import com.ticket.operationlog.Operation;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/trains")
@CrossOrigin(origins = "*")
public class AdminTrainController {

    @Resource
    private AdminTrainService adminTrainService;

    @GetMapping
    public ResponseUtil.Result<List<Train>> list() {
        return ResponseUtil.success(adminTrainService.list());
    }

    @OperationLog(module = Module.TRAIN, operation = Operation.CREATE_TRAIN)
    @PostMapping
    public ResponseUtil.Result<Train> add(@RequestBody Train train) {
        adminTrainService.save(train);
        return ResponseUtil.success("添加成功", train);
    }

    @OperationLog(module = Module.TRAIN, operation = Operation.UPDATE_TRAIN)
    @PutMapping("/{id}")
    public ResponseUtil.Result<Train> update(@PathVariable("id") Long id, @RequestBody Train train) {
        train.setId(id);
        adminTrainService.updateById(train);
        return ResponseUtil.success("更新成功", train);
    }

    @OperationLog(module = Module.TRAIN, operation = Operation.UPDATE_TRAIN_STATUS)
    @PutMapping("/{id}/status")
    public ResponseUtil.Result<?> updateStatus(@PathVariable("id") Long id, @RequestBody UpdateStatusRequest request) {
        Train train = adminTrainService.getById(id);
        if (train == null) {
            return ResponseUtil.error("车次不存在");
        }
        train.setStatus(request.getStatus());
        adminTrainService.updateById(train);
        return ResponseUtil.success("状态更新成功");
    }

    // 弃用接口
    @Deprecated
    @OperationLog(module = Module.TRAIN, operation = Operation.SET_REMAINING_TICKETS, description = "按日期/区段配置库存")
    @PostMapping("/{id}/stock")
    public ResponseUtil.Result<?> setStock(@PathVariable("id") Long id, @RequestBody List<StockItem> stockItems) {
        return ResponseUtil.success("余票设置成功");
    }

    @Data
    public static class UpdateStatusRequest {
        private Integer status;
    }

    @Data
    public static class StockItem {
        private String trainDate;
        private String startStation;
        private String endStation;
        private Integer seatType;
        private Integer availableSeats;
        private Double price;
    }
}
