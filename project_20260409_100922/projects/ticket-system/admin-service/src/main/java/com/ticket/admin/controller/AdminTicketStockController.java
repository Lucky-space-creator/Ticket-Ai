package com.ticket.admin.controller;

import com.ticket.admin.dto.TicketStockPageResult;
import com.ticket.admin.dto.TicketStockSaleEnabledRequest;
import com.ticket.admin.dto.TicketStockSeatsRequest;
import com.ticket.admin.service.AdminTicketStockService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/ticket-stocks")
@CrossOrigin(origins = "*")
public class AdminTicketStockController {

    @Resource
    private AdminTicketStockService adminTicketStockService;

    /**
     * 分页查询 ticket_stock，可按线段车次号、日期、开售状态筛选。
     */
    @GetMapping
    public ResponseUtil.Result<TicketStockPageResult> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long trainId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate trainDate,
            @RequestParam(required = false) String trainNo,
            @RequestParam(required = false) Integer saleEnabled
    ) {
        TicketStockPageResult data = adminTicketStockService.page(current, size, trainId, trainDate, trainNo, saleEnabled);
        return ResponseUtil.success(data);
    }

    /**
     * 调整总座与余座；变更后刷新 train-service 余票缓存。
     */
    @PutMapping("/{id}/seats")
    public ResponseUtil.Result<Void> updateSeats(@PathVariable Long id, @RequestBody TicketStockSeatsRequest body) {
        adminTicketStockService.updateSeats(id, body);
        return ResponseUtil.success("更新成功", null);
    }

    @PutMapping("/{id}/sale-enabled")
    public ResponseUtil.Result<Void> updateSaleEnabled(@PathVariable Long id, @RequestBody TicketStockSaleEnabledRequest body) {
        adminTicketStockService.updateSaleEnabled(id, body);
        return ResponseUtil.success("更新成功", null);
    }
}
