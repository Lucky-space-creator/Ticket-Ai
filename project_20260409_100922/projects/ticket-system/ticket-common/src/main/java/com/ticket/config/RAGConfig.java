package com.ticket.config;

import com.ticket.tool.AIBusinessTool;
import com.ticket.util.UserContext;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import com.ticket.service.KnowledgeAssistant;
import com.ticket.service.StreamingKnowledgeAssistant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * RAG 配置
 */
@Configuration
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class RAGConfig {
    private static final Logger log = LoggerFactory.getLogger(RAGConfig.class);

    @Value("${rag.max-results:3}")
    private int maxResults;

    @Value("${rag.min-score:0.7}")
    private Double minScore;

    // 存储每个用户的聊天记忆
    private final ConcurrentHashMap<String, ChatMemory> chatMemoryMap = new ConcurrentHashMap<>();

    /**
     * 创建基于用户ID的聊天记忆
     */
    @Bean
    public ChatMemory chatMemory() {
        // 返回一个根据当前用户ID动态选择记忆的ChatMemory
        return new UserScopedChatMemory();
    }

    /**
     * 用户作用域的聊天记忆实现
     * 根据当前用户ID从map中获取对应的MessageWindowChatMemory
     */
    private class UserScopedChatMemory implements ChatMemory {
        @Override
        public Object id() {
            return getCurrentUserId();
        }

        @Override
        public void add(ChatMessage message) {
            getDelegate().add(message);
        }

        @Override
        public List<ChatMessage> messages() {
            return getDelegate().messages();
        }

        @Override
        public void clear() {
            getDelegate().clear();
        }

        private String getCurrentUserId() {
            Long userId = UserContext.getCurrentUserId();
            return userId != null ? userId.toString() : "anonymous";
        }

        private ChatMemory getDelegate() {
            // 获取当前用户ID
            String userId = getCurrentUserId();
            return chatMemoryMap.computeIfAbsent(userId, k -> MessageWindowChatMemory.withMaxMessages(10));
        }
    }


    /**
     * 创建知识检索服务
     * @param chatLanguageModel 聊天模型
     * @param embeddingStore 向量数据库
     * @param chatMemory 会话记忆
     * @param embeddingModel 嵌入模型
     * @param aiBusinessTool 业务工具
     */
    @Bean
    public KnowledgeAssistant knowledgeAssistant(
            ChatLanguageModel chatLanguageModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMemory chatMemory,
            EmbeddingModel embeddingModel,
            AIBusinessTool aiBusinessTool) {

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

            // 组装 AI 服务，集成业务工具，使用基于用户ID的聊天记忆
            return AiServices.builder(KnowledgeAssistant.class)
                    .chatLanguageModel(chatLanguageModel)
                    .contentRetriever(retriever)
                    .chatMemory(chatMemory)
                    .tools(aiBusinessTool)
                    .build();
        } catch (Exception e) {
            log.error("初始化 RAG 服务失败: {}", e.getMessage(), e);

            // RAG初始化失败时抛出异常，由Service层统一返回降级消息
            throw new IllegalStateException("RAG服务初始化失败，AI聊天不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 创建流式知识检索服务
     * @param streamingChatLanguageModel 流式聊天模型
     * @param embeddingStore 向量数据库
     * @param chatMemory 会话记忆
     * @param embeddingModel 嵌入模型
     * @param aiBusinessTool 业务工具
     */
    @Bean
    public StreamingKnowledgeAssistant streamingKnowledgeAssistant(
            StreamingChatLanguageModel streamingChatLanguageModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatMemory chatMemory,
            EmbeddingModel embeddingModel,
            AIBusinessTool aiBusinessTool) {

        log.info("=== 初始化流式 RAG 服务（集成业务工具） ===");

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
        if (streamingChatLanguageModel == null) {
            log.warn("StreamingChatLanguageModel 为 null，流式 RAG 服务可能无法正常工作");
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

            // 组装流式 AI 服务，集成业务工具，使用基于用户ID的聊天记忆
            return AiServices.builder(StreamingKnowledgeAssistant.class)
                    .streamingChatLanguageModel(streamingChatLanguageModel)
                    .contentRetriever(retriever)
                    .chatMemory(chatMemory)
                    .tools(aiBusinessTool)
                    .build();
        } catch (Exception e) {
            log.error("初始化流式 RAG 服务失败: {}", e.getMessage(), e);

            // 返回一个降级的服务，当RAG失败时提供有用的信息
            return question -> Flux.just("""
                    抱歉，智能客服系统当前正在维护中，预计10分钟内恢复。
                    您的问题已被记录，请稍后再试。

                    在此期间，您可以：
                    1. 查看【常见问题】页面
                    2. 拨打客服热线：12306
                    3. 使用网站上的其他自助服务""");
        }
    }
}