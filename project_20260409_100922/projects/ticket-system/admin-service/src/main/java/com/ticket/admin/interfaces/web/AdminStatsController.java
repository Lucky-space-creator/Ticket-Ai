package com.ticket.admin.interfaces.web;

import com.ticket.admin.service.StatsService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端统计控制器
 */
@RestController
@RequestMapping("/api/admin/stats")
@CrossOrigin(origins = "*")
public class AdminStatsController {

    @Resource
    private StatsService statsService;

    /**
     * 获取数据概览
     */
    @GetMapping("/overview")
    public ResponseUtil.Result<Map<String, Object>> getOverview() {
        Map<String, Object> overview = statsService.getOverviewStats();
        return ResponseUtil.success(overview);
    }

    /**
     * 获取趋势数据
     * @param type 趋势类型：daily, weekly, monthly
     */
    @GetMapping("/trend/{type}")
    public ResponseUtil.Result<Map<String, Object>> getTrend(@PathVariable String type) {
        Map<String, Object> trend = statsService.getTrendStats(type);
        return ResponseUtil.success(trend);
    }
}