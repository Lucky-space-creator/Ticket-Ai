package com.ticket.admin.interfaces.web;

import com.ticket.admin.application.catalog.AdminTrainService;
import com.ticket.entity.Train;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
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

    @PostMapping
    public ResponseUtil.Result<Train> add(@RequestBody Train train) {
        adminTrainService.save(train);
        return ResponseUtil.success("添加成功", train);
    }

    @PutMapping("/{id}")
    public ResponseUtil.Result<Train> update(@PathVariable Long id, @RequestBody Train train) {
        train.setId(id);
        adminTrainService.updateById(train);
        return ResponseUtil.success("更新成功", train);
    }

    @PutMapping("/{id}/status")
    public ResponseUtil.Result<?> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        Train train = adminTrainService.getById(id);
        if (train == null) {
            return ResponseUtil.error("车次不存在");
        }
        train.setStatus(request.getStatus());
        adminTrainService.updateById(train);
        return ResponseUtil.success("状态更新成功");
    }

    @PostMapping("/{id}/stock")
    public ResponseUtil.Result<?> setStock(@PathVariable Long id, @RequestBody List<StockItem> stockItems) {
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
