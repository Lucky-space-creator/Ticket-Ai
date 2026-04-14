package com.ticket.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import com.ticket.service.KnowledgeAssistant;
import com.ticket.tool.TicketBusinessTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * RAG 配置
 */
@Configuration
public class RAGConfig {
    private static final Logger log = LoggerFactory.getLogger(RAGConfig.class);

    @Value("${rag.max-results:3}")
    private int maxResults;

    @Value("${rag.min-score:0.7}")
    private Double minScore;

    @Bean
    public KnowledgeAssistant knowledgeAssistant(
            ChatLanguageModel chatLanguageModel,
            EmbeddingStore embeddingStore,
            EmbeddingModel embeddingModel,
            TicketBusinessTool ticketBusinessTool) {

        log.info("=== 初始化 RAG 服务（集成业务工具） ===");
        
        // 验证配置参数
        if (maxResults <= 0 || maxResults > 20) {
            log.warn("maxResults 配置值 {} 超出合理范围 (1-20)，使用默认值 3", maxResults);
            maxResults = 3;
        }
        if (minScore < 0.0 || minScore > 1.0) {
            log.warn("minScore 配置值 {} 超出合理范围 (0.0-1.0)，使用默认值 0.7", minScore);
            minScore = 0.7;
        }
        
        log.info("最大检索数: {}", maxResults);
        log.info("最低相似度: {}", minScore);
        
        // 检查聊天模型是否有效
        if (chatLanguageModel == null) {
            log.warn("ChatLanguageModel 为 null，RAG 服务可能无法正常工作");
        }
        
        // 检查嵌入模型是否有效
        if (embeddingModel == null) {
            log.warn("EmbeddingModel 为 null，知识检索功能可能无法正常工作");
        }

        try {
            // 创建内容检索器（从向量数据库中检索相关知识）
            EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            // 组装 AI 服务，集成业务工具
            return AiServices.builder(KnowledgeAssistant.class)
                    .chatLanguageModel(chatLanguageModel)
                    .contentRetriever(retriever)
                    .tools(ticketBusinessTool)
                    .build();
        } catch (Exception e) {
            log.error("初始化 RAG 服务失败: {}", e.getMessage(), e);
            
            // 返回一个降级的服务，当RAG失败时提供有用的信息
            return question -> {
                log.info("使用降级服务处理问题: {}", question);
                return "抱歉，智能客服系统当前正在维护中，预计10分钟内恢复。\n\n" +
                       "您的问题已被记录，请稍后再试。\n\n" +
                       "在此期间，您可以：\n" +
                       "1. 查看【常见问题】页面\n" +
                       "2. 拨打客服热线：12306\n" +
                       "3. 使用网站上的其他自助服务\n\n" +
                       "原始问题：" + question;
            };
        }
    }
}