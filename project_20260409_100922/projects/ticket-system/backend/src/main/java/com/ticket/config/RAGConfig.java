package com.ticket.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import com.ticket.service.KnowledgeAssistant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    }
}