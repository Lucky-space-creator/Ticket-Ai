package com.ticket.service;

import java.util.Map;

/**
 * 统计服务接口
 */
public interface StatsService {

    /**
     * 获取数据概览统计
     */
    Map<String, Object> getOverviewStats();

    /**
     * 获取趋势统计
     * @param type 趋势类型：daily, weekly, monthly
     */
    Map<String, Object> getTrendStats(String type);
}