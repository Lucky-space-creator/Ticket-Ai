package com.ticket.aichat.config;

import com.ticket.aichat.manual.ManualGovernedContentRetriever;
import com.ticket.aichat.manual.ManualKbGovernance;
import com.ticket.aichat.service.KnowledgeAssistant;
import com.ticket.aichat.service.StreamingKnowledgeAssistant;
import com.ticket.aichat.tool.AIBusinessTool;
import com.ticket.util.UserContext;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 与 LangChain4j AI 服务装配（仅 aichat-service）
 */
@Configuration
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class RAGConfig {

    private static final Logger log = LoggerFactory.getLogger(RAGConfig.class);

    @Value("${rag.max-results:3}")
    private int maxResults;

    @Value("${rag.min-score:0.7}")
    private double minScore;

    private final ConcurrentHashMap<String, ChatMemory> chatMemoryMap = new ConcurrentHashMap<>();

    @Bean
    public ChatMemory chatMemory() {
        return new UserScopedChatMemory();
    }

    /**
     * 创建 ManualGovernedContentRetriever
     * @param embeddingStore 向量库
     * @param embeddingModel 向量模型
     * @param manualKbGovernance 知识库治理
     * @param expandMultiplier 检索扩展倍数
     * @param filteredCap 过滤数量
     * @return
     */
    @Bean
    public ContentRetriever manualGovernedRagRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ManualKbGovernance manualKbGovernance,
            @Value("${rag.retrieval-expand-multiplier:5}") int expandMultiplier,
            @Value("${rag.filtered-cap:40}") int filteredCap) {
        int saneMax = saneMaxResults(maxResults);
        double saneMin = saneMinScore(minScore);

        int expanded = Math.min(
                saneMax * Math.max(expandMultiplier, 1),
                Math.max(filteredCap, saneMax));

        log.info("RAG检索: outputMax={}, expandedRetrieve={}, minScore={}", saneMax, expanded, saneMin);

        EmbeddingStoreContentRetriever inner = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(expanded)
                .minScore(saneMin)
                .build();

        return new ManualGovernedContentRetriever(inner, manualKbGovernance, saneMax);
    }

    @Bean
    public KnowledgeAssistant knowledgeAssistant(
            ChatLanguageModel chatLanguageModel,
            ContentRetriever manualGovernedRagRetriever,
            ChatMemory chatMemory,
            AIBusinessTool aiBusinessTool) {

        log.info("=== 初始化 RAG 服务（集成业务工具） ===");

        if (chatLanguageModel == null) {
            log.warn("ChatLanguageModel 为 null，RAG 服务可能无法正常工作");
        }

        try {
            return AiServices.builder(KnowledgeAssistant.class)
                    .chatLanguageModel(chatLanguageModel)
                    .contentRetriever(manualGovernedRagRetriever)
                    .chatMemory(chatMemory)
                    .tools(aiBusinessTool)
                    .build();
        } catch (Exception e) {
            log.error("初始化 RAG 服务失败: {}", e.getMessage(), e);
            throw new IllegalStateException("RAG服务初始化失败，AI聊天不可用: " + e.getMessage(), e);
        }
    }

    @Bean
    public StreamingKnowledgeAssistant streamingKnowledgeAssistant(
            StreamingChatLanguageModel streamingChatLanguageModel,
            ContentRetriever manualGovernedRagRetriever,
            ChatMemory chatMemory,
            AIBusinessTool aiBusinessTool) {

        log.info("=== 初始化流式 RAG 服务（集成业务工具） ===");

        if (streamingChatLanguageModel == null) {
            log.warn("StreamingChatLanguageModel 为 null，流式 RAG 服务可能无法正常工作");
        }

        try {
            return AiServices.builder(StreamingKnowledgeAssistant.class)
                    .streamingChatLanguageModel(streamingChatLanguageModel)
                    .contentRetriever(manualGovernedRagRetriever)
                    .chatMemory(chatMemory)
                    .tools(aiBusinessTool)
                    .build();
        } catch (Exception e) {
            log.error("初始化流式 RAG 服务失败: {}", e.getMessage(), e);
            return question -> Flux.just("""
                    抱歉，智能客服系统当前正在维护中，预计10分钟内恢复。
                    您的问题已被记录，请稍后再试。

                    在此期间，您可以：
                    1. 查看【常见问题】页面
                    2. 拨打客服热线：12306
                    3. 使用网站上的其他自助服务""");
        }
    }

    private int saneMaxResults(int mr) {
        if (mr <= 0 || mr > 20) {
            log.warn("maxResults 配置值 {} 超出合理范围 (1-20)，使用默认值 3", mr);
            return 3;
        }
        return mr;
    }

    private double saneMinScore(double ms) {
        if (ms < 0.0 || ms > 1.0) {
            log.warn("minScore 配置值 {} 超出合理范围 (0.0-1.0)，使用默认值 0.7", ms);
            return 0.7;
        }
        return ms;
    }

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
            String userId = getCurrentUserId();
            return chatMemoryMap.computeIfAbsent(userId, k -> MessageWindowChatMemory.withMaxMessages(10));
        }
    }
}
