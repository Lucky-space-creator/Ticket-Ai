package com.ticket.util;

/**
 * Token 计数工具类
 * 用于估算文本的 Token 数量
 *
 * <p>说明：
 * <ul>
 *   <li>同步场景：LangChain4j Result<String>; 可获取精确 TokenUsage</li>
 *   <li>流式场景：声明式 Flux<String>; 无法获取精确 TokenUsage，
 *       使用本工具类基于字符数进行估算</li>
 * </ul>
 *
 * <p>估算规则（参考 OpenAI tiktoken GPT-3.5-turbo 标准）：
 * <ul>
 *   <li>中文字符：约 1-2 字符 / token</li>
 *   <li>英文字符：约 4 字符 / token</li>
 *   <li>混合文本按比例加权估算</li>
 * </ul>
 */
public final class TokenCountUtil {

    private static final int CHARS_PER_TOKEN_CN = 2;
    private static final int CHARS_PER_TOKEN_EN = 4;
    private static final double CN_RATIO_THRESHOLD = 0.3;

    private TokenCountUtil() {
        // 工具类禁止实例化
    }

    /**
     * 估算文本的 Token 数量
     *
     * @param text 输入文本
     * @return 估算的 token 数量（向下取整，最小为0）
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int totalChars = text.length();
        if (totalChars == 0) {
            return 0;
        }

        int cnCharCount = countChineseCharacters(text);
        double cnRatio = (double) cnCharCount / totalChars;

        int charsPerToken;
        if (cnRatio >= CN_RATIO_THRESHOLD) {
            // 中文主导或混合文本
            charsPerToken = CHARS_PER_TOKEN_CN;
        } else {
            // 英文主导
            charsPerToken = CHARS_PER_TOKEN_EN;
        }

        return Math.max(1, totalChars / charsPerToken);
    }

    /**
     * 统计文本中的中文字符数量
     *
     * @param text 输入文本
     * @return 中文字符数量
     */
    private static int countChineseCharacters(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChineseCharacter(c)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断是否为中文字符（CJK Unified Ideographs 范围）
     *
     * @param c 字符
     * @return 是否为中文
     */
    private static boolean isChineseCharacter(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION;
    }
}
