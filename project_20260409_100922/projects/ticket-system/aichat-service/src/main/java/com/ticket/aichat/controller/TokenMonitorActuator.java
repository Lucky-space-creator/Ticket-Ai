package com.ticket.aichat.controller;

import com.ticket.util.TokenMonitor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Actuator 端点：暴露 Token 监控数据。
 * <p>
 * 访问路径: {@code /actuator/token-stats}
 * <p>
 * 返回 JSON 格式的全局统计、Per-Agent 统计、用户数等信息，
 * 可接入 ELK / Grafana 进行可视化监控。
 */
@Component
@Endpoint(id = "token-stats")
public class TokenMonitorActuator {

    private final TokenMonitor tokenMonitor;

    public TokenMonitorActuator(TokenMonitor tokenMonitor) {
        this.tokenMonitor = tokenMonitor;
    }

    /**
     * GET /actuator/token-stats
     */
    @ReadOperation
    public Map<String, Object> tokenStats() {
        return tokenMonitor.getGlobalStats();
    }
}
