package com.ticket.admin.controller;

import com.ticket.admin.client.TrainStockCacheFeignClient;
import com.ticket.admin.mapper.TicketStockMapper;
import com.ticket.entity.TicketStock;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ticket-stocks")
@CrossOrigin(origins = "*")
public class AdminTicketStockController {

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Resource
    private TrainStockCacheFeignClient trainStockCacheFeignClient;

    @PutMapping("/{id}/sale-enabled")
    public ResponseUtil.Result<Void> updateSaleEnabled(@PathVariable Long id, @RequestBody SaleEnabledBody body) {
        if (body == null || (body.getSaleEnabled() != 0 && body.getSaleEnabled() != 1)) {
            return ResponseUtil.error("saleEnabled 须为 0 或 1");
        }
        TicketStock ts = ticketStockMapper.selectById(id);
        if (ts == null) {
            return ResponseUtil.error("库存记录不存在");
        }
        ts.setSaleEnabled(body.getSaleEnabled());
        ticketStockMapper.updateById(ts);
        trainStockCacheFeignClient.invalidateTicketStock(id);
        return ResponseUtil.success("更新成功", null);
    }

    @Data
    public static class SaleEnabledBody {
        /** 1 开售，0 停售 */
        private Integer saleEnabled;
    }
}
