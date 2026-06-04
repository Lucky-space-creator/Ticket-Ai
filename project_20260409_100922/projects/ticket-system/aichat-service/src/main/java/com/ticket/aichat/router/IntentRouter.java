package com.ticket.aichat.router;

import com.ticket.dto.TokenUsage;
import com.ticket.util.TokenMonitor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 意图路由器：规则优先 + LLM 兜底。
 * <p>
 * 路由策略：
 * <ol>
 *   <li>关键词规则匹配（零延迟、零 Token 消耗）</li>
 *   <li>LLM 轻量分类（仅规则无法判断时调用）</li>
 * </ol>
 */
@Slf4j
@Component
public class IntentRouter {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Resource
    private TokenMonitor tokenMonitor;

    /**
     * 对用户问题进行意图分类。
     *
     * @param question 用户问题
     * @return 意图类型
     */
    public IntentType classify(String question) {
        if (question == null || question.isBlank()) {
            return IntentType.GREETING;
        }
        String q = question.toLowerCase(Locale.ROOT).trim();

        // 第一层：关键词规则（零延迟、零成本）
        if (containsHumanKeyword(q)) {
            return IntentType.HUMAN_TRANSFER;
        }
        if (containsTrainKeyword(q)) {
            return IntentType.TRAIN_QUERY;
        }
        if (containsOrderKeyword(q)) {
            return IntentType.ORDER;
        }
        if (containsProfileKeyword(q)) {
            return IntentType.PROFILE;
        }
        if (containsGreeting(q)) {
            return IntentType.GREETING;
        }

        // 第二层：LLM 兜底分类
        return classifyByLLM(question);
    }

    // ==================== LLM 兜底分类 ====================

    private IntentType classifyByLLM(String question) {
        if (chatLanguageModel == null) {
            log.warn("ChatLanguageModel 不可用，意图分类默认返回 KNOWLEDGE");
            return IntentType.KNOWLEDGE;
        }

        String systemPrompt = """
                你是一个意图分类器。将用户问题分类到以下类别之一，只输出标签，不要输出其他内容：
                - TRAIN_QUERY（查车次、查时刻表、查票价、查停靠站）
                - ORDER（买票、购票、退票、查订单、支付、改签）
                - KNOWLEDGE（铁路政策、乘车规定、退改签规则、安检规定等知识性问题）
                - PROFILE（个人信息、联系人管理、身份证、姓名）
                - HUMAN_TRANSFER（转人工、找客服）
                - GREETING（打招呼、问你是谁）
                """;

        try {
            Response<AiMessage> response = chatLanguageModel.generate(
                    List.of(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(question)));

            String label = response.content().text().trim().toUpperCase(Locale.ROOT);

            // 记录分类 Token 消耗
            if (response.tokenUsage() != null) {
                tokenMonitor.record(TokenUsage.precise(
                        response.tokenUsage().inputTokenCount(),
                        response.tokenUsage().outputTokenCount(),
                        "classify"), null);
            }

            return parseIntent(label);
        } catch (Exception e) {
            log.error("LLM 意图分类失败，默认返回 KNOWLEDGE: {}", e.getMessage());
            return IntentType.KNOWLEDGE;
        }
    }

    private IntentType parseIntent(String label) {
        // 提取标签中的关键词（LLM 可能输出多余内容）
        for (IntentType type : IntentType.values()) {
            if (label.contains(type.name())) {
                return type;
            }
        }
        log.warn("LLM 返回的意图标签无法解析: {}", label);
        return IntentType.KNOWLEDGE;
    }

    // ==================== 关键词匹配 ====================

    private boolean containsHumanKeyword(String q) {
        return containsAny(q, "转人工", "人工客服", "真人客服", "转接人工",
                "人工坐席", "我要人工", "接人工");
    }

    private boolean containsTrainKeyword(String q) {
        return containsAny(q, "查车次", "车次查询", "列车时刻", "火车时刻",
                "高铁", "动车", "火车票", "时刻表", "停靠站",
                "几点发车", "几点到", "票价", "座位",
                "从", "到") && q.length() > 3;
    }

    private boolean containsOrderKeyword(String q) {
        return containsAny(q, "买票", "购票", "订票", "下单", "退票",
                "退款", "订单", "支付", "改签", "我的订单",
                "出票", "退票费", "手续费");
    }

    private boolean containsProfileKeyword(String q) {
        return containsAny(q, "个人信息", "联系人", "常用联系人", "身份证",
                "真实姓名", "修改姓名", "修改信息", "我的信息",
                "乘客信息", "添加联系人", "删除联系人");
    }

    private boolean containsGreeting(String q) {
        return containsAny(q, "你好", "您好", "hi", "hello", "嗨",
                "你是谁", "介绍一下", "早上好", "下午好", "晚上好");
    }

    /**
     * 检查文本是否包含任一关键词
     */
    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
