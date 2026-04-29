package com.ticket.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI聊天链路专用计时器
 * 提供关键节点的耗时统计和结构化日志输出
 * 配合ELK全链路追踪使用
 */
@Slf4j
public class AiChatStopWatch {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 任务名称 */
    private final String taskName;

    /** 开始时间（毫秒） */
    private long startTimeMillis;

    /** 上一个检查点时间（毫秒） */
    private long lastCheckpointMillis;

    /** 各检查点耗时记录 */
    private final LinkedHashMap<String, Long> checkpoints;

    /** 是否已完成 */
    private boolean isRunning;

    /**
     * 创建AI聊天计时器
     * @param taskName 任务名称（如"ai-chat-sync"、"ai-chat-stream"）
     */
    public AiChatStopWatch(String taskName) {
        this.taskName = taskName;
        this.checkpoints = new LinkedHashMap<>();
        this.isRunning = false;
    }

    /**
     * 开始计时
     * @return 当前实例（支持链式调用）
     */
    public AiChatStopWatch start() {
        this.startTimeMillis = System.currentTimeMillis();
        this.lastCheckpointMillis = this.startTimeMillis;
        this.isRunning = true;
        return this;
    }

    /**
     * 记录检查点并计算与上一节点的时间差
     * @param checkpointName 检查点名称（如 "route_judge", "llm_call", "db_save"）
     * @return 当前实例（支持链式调用）
     */
    public AiChatStopWatch checkpoint(String checkpointName) {
        if (!isRunning) {
            throw new IllegalStateException("请先调用 start() 开始计时");
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastCheckpointMillis;
        checkpoints.put(checkpointName, elapsed);
        this.lastCheckpointMillis = now;
        return this;
    }

    /**
     * 结束计时并输出结构化日志
     * 日志格式为JSON字符串，方便ELK解析
     */
    public void stopAndLog() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        long totalElapsed = System.currentTimeMillis() - startTimeMillis;

        // 构建结构化日志（JSON格式）
        StringBuilder sb = new StringBuilder(256);
        sb.append("[AiChatTrace] {");
        appendField(sb, "taskName", taskName);
        sb.append(", ");
        appendField(sb, "traceId", TraceContext.getTraceId());
        sb.append(", ");
        appendField(sb, "totalMs", String.valueOf(totalElapsed));
        sb.append(", ");
        appendField(sb, "startTime",
                LocalDateTime.now().minusNanos(totalElapsed * 1_000_000).format(FORMATTER));
        sb.append(", \"checkpoints\": {");

        boolean first = true;
        for (Map.Entry<String, Long> entry : checkpoints.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\": ")
              .append(entry.getValue()).append("ms");
        }
        sb.append("}}");

        log.info(sb.toString());
    }

    /**
     * 获取总耗时（毫秒），不停止计时
     * @return 从start()到当前的毫秒数
     */
    public long getTotalTimeMillis() {
        if (!isRunning) {
            return 0;
        }
        return System.currentTimeMillis() - startTimeMillis;
    }

    /**
     * 获取某个检查点的耗时
     * @param checkpointName 检查点名称
     * @return 该段耗时（毫秒），未找到返回-1
     */
    public long getCheckpointTime(String checkpointName) {
        Long time = checkpoints.get(checkpointName);
        return time != null ? time : -1L;
    }

    /**
     * 向StringBuilder追加JSON字段
     */
    private void appendField(StringBuilder sb, String key, String value) {
        sb.append("\"").append(key).append("\": ")
          .append(value == null ? "\"null\"" : "\"" + value + "\"");
    }
}