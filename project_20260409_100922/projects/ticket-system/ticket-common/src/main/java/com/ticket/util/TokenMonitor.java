package com.ticket.util;

import com.ticket.dto.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Token 监控工具箱：统一采集、统计、预算、暴露。
 * <p>
 * 职责：
 * <ol>
 *   <li>统一提取 LangChain4j {@code Result<String>} 中的 TokenUsage</li>
 *   <li>统一估算流式/异常场景的 Token 数</li>
 *   <li>按 userId / agentName 聚合统计</li>
 *   <li>预算告警（单用户日限额、单请求限额）</li>
 *   <li>暴露给 Actuator 端点 {@code /actuator/token-stats}</li>
 * </ol>
 */
@Slf4j
@Component
public class TokenMonitor {

    // ==================== 全局统计（LongAdder 高并发友好） ====================
    private final LongAdder totalInputTokens = new LongAdder();
    private final LongAdder totalOutputTokens = new LongAdder();
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder estimatedCount = new LongAdder();

    // ==================== Per-Agent 统计 ====================
    private final ConcurrentHashMap<String, AgentTokenStats> agentStats =
            new ConcurrentHashMap<>();

    // ==================== Per-User 每日累计 ====================
    private final ConcurrentHashMap<Long, AtomicLong> userDailyTokens =
            new ConcurrentHashMap<>();

    // ==================== 预算配置 ====================
    private long userDailyLimit = 50_000L;
    private int singleRequestLimit = 8_000;

    // ==================== 核心方法 ====================

    /**
     * 记录一次 Token 消耗（核心入口）
     *
     * @param usage  Token 使用记录
     * @param userId 当前用户 ID（可为 null）
     */
    public void record(TokenUsage usage, Long userId) {
        if (usage == null) {
            return;
        }

        // 1. 全局统计
        totalInputTokens.add(usage.inputTokens());
        totalOutputTokens.add(usage.outputTokens());
        totalRequests.increment();
        if (usage.estimated()) {
            estimatedCount.increment();
        }

        // 2. Per-Agent 统计
        agentStats.computeIfAbsent(usage.source(), k -> new AgentTokenStats())
                .add(usage.inputTokens(), usage.outputTokens());

        // 3. Per-User 统计 + 预算检查
        if (userId != null) {
            AtomicLong daily = userDailyTokens.computeIfAbsent(
                    userId, k -> new AtomicLong(0));
            long newTotal = daily.addAndGet(usage.totalTokens());
            if (newTotal > userDailyLimit) {
                log.warn("[TokenMonitor] 用户 {} 今日 Token 用量({})超过限额({})",
                        userId, newTotal, userDailyLimit);
            }
        }

        // 4. 单次请求限额检查
        if (usage.totalTokens() > singleRequestLimit) {
            log.warn("[TokenMonitor] 单次请求 Token({})超过限额({}), source={}",
                    usage.totalTokens(), singleRequestLimit, usage.source());
        }

        // 5. 结构化日志（ELK 可解析）
        log.info("[TokenMonitor] input={}, output={}, estimated={}, source={}, userId={}",
                usage.inputTokens(), usage.outputTokens(), usage.estimated(),
                usage.source(), userId);
    }

    // ==================== 提取方法 ====================

    /**
     * 根据精确 token 数值构建 TokenUsage 并记录。
     * <p>
     * 若 inputTokens 和 outputTokens 均为 0，返回 null（调用方应 fallback 到估算）。
     *
     * @param inputTokens  输入 token 数
     * @param outputTokens 输出 token 数
     * @param source       来源标识（如 "agent:FAQAgent"）
     * @return 精确 TokenUsage，若 token 数均为 0 则返回 null
     */
    public TokenUsage extractPrecise(int inputTokens, int outputTokens, String source) {
        if (inputTokens == 0 && outputTokens == 0) {
            return null;
        }
        return TokenUsage.precise(inputTokens, outputTokens, source);
    }

    /**
     * 估算文本的输出 Token 数（流式/异常场景 fallback）。
     * 替代散落的 {@code TokenCountUtil.estimate()} 调用。
     *
     * @param text   输出文本
     * @param source 来源标识
     * @return 估算的 TokenUsage
     */
    public TokenUsage estimateOutput(String text, String source) {
        int estimated = TokenCountUtil.estimate(text);
        return TokenUsage.estimated(estimated, source);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取全局统计快照（Actuator 用）
     */
    public Map<String, Object> getGlobalStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long input = totalInputTokens.sum();
        long output = totalOutputTokens.sum();
        long req = totalRequests.sum();
        long est = estimatedCount.sum();

        stats.put("totalInputTokens", input);
        stats.put("totalOutputTokens", output);
        stats.put("totalTokens", input + output);
        stats.put("totalRequests", req);
        stats.put("estimatedCount", est);
        stats.put("preciseRate", req == 0 ? 1.0 : (double) (req - est) / req);
        stats.put("perAgent", getAgentStats());
        stats.put("userCount", userDailyTokens.size());
        return stats;
    }

    /**
     * 获取 Per-Agent 统计
     */
    public Map<String, Map<String, Long>> getAgentStats() {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        agentStats.forEach((name, stats) -> {
            Map<String, Long> s = new LinkedHashMap<>();
            s.put("inputTokens", stats.inputTokens.sum());
            s.put("outputTokens", stats.outputTokens.sum());
            s.put("calls", stats.calls.sum());
            result.put(name, s);
        });
        return result;
    }

    /**
     * 获取指定用户的今日 Token 用量
     */
    public long getUserDailyUsage(Long userId) {
        AtomicLong daily = userDailyTokens.get(userId);
        return daily != null ? daily.get() : 0;
    }

    /**
     * 检查用户是否超出日预算
     */
    public boolean isUserOverBudget(Long userId) {
        return getUserDailyUsage(userId) > userDailyLimit;
    }

    // ==================== 配置方法 ====================

    public void setUserDailyLimit(long limit) {
        this.userDailyLimit = limit;
    }

    public void setSingleRequestLimit(int limit) {
        this.singleRequestLimit = limit;
    }

    /**
     * 每日零点重置用户统计（由定时任务调用）
     */
    public void resetDailyUserStats() {
        userDailyTokens.clear();
        log.info("[TokenMonitor] 每日用户 Token 统计已重置");
    }

    // ==================== 内部类 ====================

    /**
     * Per-Agent 统计计数器
     */
    private static class AgentTokenStats {
        final LongAdder inputTokens = new LongAdder();
        final LongAdder outputTokens = new LongAdder();
        final LongAdder calls = new LongAdder();

        void add(int input, int output) {
            inputTokens.add(input);
            outputTokens.add(output);
            calls.increment();
        }
    }
}
