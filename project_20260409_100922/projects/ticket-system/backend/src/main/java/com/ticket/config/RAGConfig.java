package com.ticket.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import com.ticket.service.KnowledgeAssistant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * RAG 配置
 */
@Configuration
public class RAGConfig {

    @Value("${rag.max-results:3}")
    private int maxResults;

    @Value("${rag.min-score:0.7}")
    private Double minScore;

    @Bean
    public KnowledgeAssistant knowledgeAssistant(
            ChatLanguageModel chatLanguageModel,
            EmbeddingStore embeddingStore,
            EmbeddingModel embeddingModel) {

        System.out.println("=== 初始化 RAG 服务 ===");
        System.out.println("最大检索数: " + maxResults);
        System.out.println("最低相似度: " + minScore);
        
        // 检查聊天模型是否有效
        if (chatLanguageModel == null) {
            System.err.println("警告: ChatLanguageModel 为 null，RAG 服务可能无法正常工作");
        }
        
        // 检查嵌入模型是否有效
        if (embeddingModel == null) {
            System.err.println("警告: EmbeddingModel 为 null，知识检索功能可能无法正常工作");
        }

        try {
            // 创建内容检索器（从向量数据库中检索相关知识）
            EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            // 组装 AI 服务
            return AiServices.builder(KnowledgeAssistant.class)
                    .chatLanguageModel(chatLanguageModel)
                    .contentRetriever(retriever)
                    .build();
        } catch (Exception e) {
            System.err.println("初始化 RAG 服务失败: " + e.getMessage());
            
            // 返回一个降级的服务，当RAG失败时仍然可以回答问题
            return question -> "抱歉，智能客服系统当前正在维护中。您的问题：" + question + " 已被记录，请稍后再试或联系人工客服。";
        }
    }
}