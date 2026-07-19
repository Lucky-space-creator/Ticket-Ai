package com.ticket.aichat.config;

import com.ticket.aichat.agent.FAQAgent;
import com.ticket.aichat.agent.HumanTransferAgent;
import com.ticket.aichat.agent.OrderAgent;
import com.ticket.aichat.agent.ProfileAgent;
import com.ticket.aichat.agent.TrainQueryAgent;
import com.ticket.aichat.tool.HumanTransferTools;
import com.ticket.aichat.tool.OrderTools;
import com.ticket.aichat.tool.ProfileTools;
import com.ticket.aichat.tool.TrainTools;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多 Agent 装配配置。
 * <p>
 * 为 5 个 Specialist Agent 创建 Bean：
 * <ul>
 *   <li>TrainQueryAgent — 车次查询（无记忆）</li>
 *   <li>OrderAgent — 订单操作（共享 ChatMemory，多轮收集信息）</li>
 *   <li>FAQAgent — 知识问答（RAG 检索，无工具）</li>
 *   <li>ProfileAgent — 个人信息（无记忆）</li>
 *   <li>HumanTransferAgent — 转人工（无记忆）</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class MultiAgentConfig {

    /**
     * 车次查询 Agent：无记忆，每次查询独立。
     */
    @Bean
    public TrainQueryAgent trainQueryAgent(
            ChatLanguageModel chatLanguageModel,
            TrainTools trainTools) {
        log.info("=== 初始化 TrainQueryAgent ===");
        return AiServices.builder(TrainQueryAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(trainTools)
                .build();
    }

    /**
     * 订单 Agent：有记忆，多轮收集乘客信息。
     * 共享 RAGConfig 中的 ChatMemory Bean，与 KnowledgeAssistant 同一份用户级记忆。
     * 用户画像自动注入到对话历史中。
     */
    @Bean
    public OrderAgent orderAgent(
            ChatLanguageModel chatLanguageModel,
            OrderTools orderTools,
            ChatMemory chatMemory) {
        log.info("=== 初始化 OrderAgent（共享用户级记忆） ===");
        return AiServices.builder(OrderAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .tools(orderTools)
                .build();
    }

    /**
     * 知识问答 Agent：RAG 检索，无工具。
     * 使用 {@code Result<String>} 返回类型以获取 TokenUsage。
     */
    @Bean
    public FAQAgent faqAgent(
            ChatLanguageModel chatLanguageModel,
            ContentRetriever manualRagRetriever,
            ObjectProvider<RetrievalAugmentor> hybridRetrievalAugmentor) {
        log.info("=== 初始化 FAQAgent（RAG 检索） ===");
        var builder = AiServices.builder(FAQAgent.class)
                .chatLanguageModel(chatLanguageModel);
        RetrievalAugmentor hybrid = hybridRetrievalAugmentor.getIfAvailable();
        if (hybrid != null) {
            builder.retrievalAugmentor(hybrid);
        } else {
            builder.contentRetriever(manualRagRetriever);
        }
        return builder.build();
    }

    /**
     * 个人信息 Agent：无记忆，单轮操作。
     */
    @Bean
    public ProfileAgent profileAgent(
            ChatLanguageModel chatLanguageModel,
            ProfileTools profileTools) {
        log.info("=== 初始化 ProfileAgent ===");
        return AiServices.builder(ProfileAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(profileTools)
                .build();
    }

    /**
     * 转人工 Agent：无记忆，单轮转接。
     */
    @Bean
    public HumanTransferAgent humanTransferAgent(
            ChatLanguageModel chatLanguageModel,
            HumanTransferTools humanTransferTools) {
        log.info("=== 初始化 HumanTransferAgent ===");
        return AiServices.builder(HumanTransferAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(humanTransferTools)
                .build();
    }
}
