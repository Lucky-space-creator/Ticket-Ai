package com.ticket.dto;

/**
 * 单次 LLM 调用的 Token 消耗记录（不可变）。
 * <p>
 * 由 {@link com.ticket.util.TokenMonitor} 统一采集和管理，
 * 替代散落在 AIChatServiceImpl 中的 token 提取逻辑。
 */
public record TokenUsage(
        /** 输入 Token 数（含 SystemMessage + 工具描述 + 对话历史 + 用户消息） */
        int inputTokens,
        /** 输出 Token 数（LLM 生成的回复） */
        int outputTokens,
        /** 总 Token 数 */
        int totalTokens,
        /** true=基于字符数估算, false=LLM 返回的精确值 */
        boolean estimated,
        /** 来源标识，如 "agent:TrainAgent", "agent:FAQAgent", "classify" */
        String source,
        /** 记录时间戳 */
        long timestamp
) {

    /**
     * 紧凑构造器：自动计算 totalTokens
     */
    public TokenUsage {
        if (totalTokens == 0) {
            totalTokens = inputTokens + outputTokens;
        }
    }

    /**
     * 创建精确 TokenUsage（LLM 返回了 TokenUsage）
     */
    public static TokenUsage precise(int input, int output, String source) {
        return new TokenUsage(input, output, input + output, false, source,
                System.currentTimeMillis());
    }

    /**
     * 创建估算 TokenUsage（流式场景 / 异常 fallback）
     */
    public static TokenUsage estimated(int output, String source) {
        return new TokenUsage(0, output, output, true, source,
                System.currentTimeMillis());
    }
}
