package com.ticket.aichat.config;

import com.ticket.aichat.manual.ManualContentRetriever;
import com.ticket.aichat.rag.aggregate.FinalHybridContentFilter;
import com.ticket.aichat.rag.lexical.BM25ContentRetriever;
import com.ticket.aichat.rag.lexical.CalculateBM25;
import com.ticket.aichat.service.KnowledgeAssistant;
import com.ticket.aichat.service.StreamingKnowledgeAssistant;
import com.ticket.aichat.service.impl.ManualDocImpl;
import com.ticket.aichat.tool.AIBusinessTool;
import com.ticket.util.UserContext;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 装配：默认单路「向量 → 手册治理」；
 * 开启 {@code rag.hybrid.enabled} 后
 * 为「向量 + BM25 → RRF 融合 → doc 治理截断」
 * （向量打分仅由既有 {@link EmbeddingStoreContentRetriever} 完成，不再二次嵌入重排）。
 * <p>
 * 仅 aichat-service。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")  // 当启用 ai 服务时，才加载 RAGConfig
public class RAGConfig {


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
     * 检索链：首先过滤掉enabled=false的 doc，然后对剩余的 doc 进行打分，并返回打分最高的 topN 个 doc。
     * 属于稀疏检索，且结果数量多，且结果质量低。
     * @param embeddingStore 向量存储
     * @param embeddingModel 向量模型
     * @param manualKbGovernance 手册治理：检索结果由外部控制
     * @param expandMultiplier 检索结果数量：检索结果数量乘数
     * @param filteredCap 过滤后的最大数量
     * @return
     */
    @Bean
    public ContentRetriever manualRagRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ManualDocImpl manualKbGovernance,
            @Value("${rag.retrieval-expand-multiplier:5}") int expandMultiplier,
            @Value("${rag.filtered-cap:40}") int filteredCap) {
        int saneMax = saneMaxResults(maxResults);
        double saneMin = saneMinScore(minScore);

        int expanded = Math.min(
                saneMax * Math.max(expandMultiplier, 1),
                Math.max(filteredCap, saneMax));

        log.info("RAG检索(单路): outputMax={}, expandedRetrieve={}, minScore={}", saneMax, expanded, saneMin);

        EmbeddingStoreContentRetriever inner = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(expanded)
                .minScore(saneMin)
                .build();

        return new ManualContentRetriever(inner, manualKbGovernance, saneMax);
    }

    /**
     * 混合检索：向量检索 + BM25检索，由 {@link CalculateBM25} lexical 索引与治理截断。
     * @param embeddingStore 向量存储
     * @param embeddingModel 向量模型
     * @param manualDocImpl 手册治理：检索结果由外部控制
     * @param calculateBM25 lexical 索引
     * @param expandMultiplier 检索结果数量：检索结果数量乘数
     * @param filteredCap 过滤后最大的数量
     * @param lexicalTopK lexical 索引的 topK
     * @return 混合检索
     */
    @Bean
    @ConditionalOnProperty(name = "rag.hybrid.enabled", havingValue = "true")
    public RetrievalAugmentor hybridRetrievalAugmentor(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            ManualDocImpl manualDocImpl,
            CalculateBM25 calculateBM25,
            @Value("${rag.retrieval-expand-multiplier:5}") int expandMultiplier,
            @Value("${rag.filtered-cap:40}") int filteredCap,
            @Value("${rag.lexical.top-k:40}") int lexicalTopK) {
        int saneMax = saneMaxResults(maxResults);
        double saneMin = saneMinScore(minScore);
        int expanded = Math.min(
                saneMax * Math.max(expandMultiplier, 1),
                Math.max(filteredCap, saneMax));
        // lexicalK 用来控制 lexical 索引的 topK
        int lexicalK = Math.min(Math.max(lexicalTopK, saneMax), Math.max(filteredCap, saneMax));

        log.info("RAG检索(混合): denseMax={}, lexicalTopK={}, finalMax={}", expanded, lexicalK, saneMax);

        //稠密：向量检索
        ContentRetriever dense = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(expanded)
                .minScore(saneMin)
                .build();
        //稀疏：使用BM25检索
        ContentRetriever lexical = new BM25ContentRetriever(calculateBM25, lexicalK);

        //fused用来聚合向量检索结果和lexical检索结果
        ContentAggregator fused = new DefaultContentAggregator();
        //outer用来控制最终结果数量和治理
        ContentAggregator outer = new FinalHybridContentFilter(fused, manualDocImpl, saneMax);

        return DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(dense, lexical))
                .contentAggregator(outer)
                .build();
    }

    /**
     * 知识助手：单路 RAG 或混合 RAG
     * @param chatLanguageModel 语言模型
     * @param manualRagRetriever 稀疏检索过滤后的检索结果
     * @param hybridRetrievalAugmentor 混合检索
     * @param chatMemory 会话内存
     * @param aiBusinessTool 业务工具
     * @return 知识助手
     */
    @Bean
    public KnowledgeAssistant knowledgeAssistant(
            ChatLanguageModel chatLanguageModel,
            ContentRetriever manualRagRetriever,
            ObjectProvider<RetrievalAugmentor> hybridRetrievalAugmentor,
            ChatMemory chatMemory,
            AIBusinessTool aiBusinessTool) {

        log.info("=== 初始化 RAG 服务（集成业务工具） ===");

        if (chatLanguageModel == null) {
            log.warn("ChatLanguageModel 为 null，RAG 服务可能无法正常工作");
        }

        try {
            // 使用 AiServices 构建知识助手
            var builder = AiServices.builder(KnowledgeAssistant.class)  // 创建知识助手
                    .chatLanguageModel(chatLanguageModel)  // 语言模型
                    .chatMemory(chatMemory)  // 会话内存
                    .tools(aiBusinessTool);  // 业务工具
            RetrievalAugmentor hybrid = hybridRetrievalAugmentor.getIfAvailable();
            //如果混合检索不为空，则使用混合检索，否则使用单路检索
            if (hybrid != null) {
                builder.retrievalAugmentor(hybrid);
            } else {
                builder.contentRetriever(manualRagRetriever);
            }
            return builder.build();
        } catch (Exception e) {
            log.error("初始化 RAG 服务失败: {}", e.getMessage(), e);
            throw new IllegalStateException("RAG服务初始化失败，AI聊天不可用: " + e.getMessage(), e);
        }
    }

    @Bean
    public StreamingKnowledgeAssistant streamingKnowledgeAssistant(
            StreamingChatLanguageModel streamingChatLanguageModel,
            ContentRetriever manualGovernedRagRetriever,
            ObjectProvider<RetrievalAugmentor> hybridRetrievalAugmentor,
            ChatMemory chatMemory,
            AIBusinessTool aiBusinessTool) {

        log.info("=== 初始化流式 RAG 服务（集成业务工具） ===");


        if (streamingChatLanguageModel == null) {
            log.warn("StreamingChatLanguageModel 为 null，流式 RAG 服务可能无法正常工作");
        }

        try {
            var builder = AiServices.builder(StreamingKnowledgeAssistant.class)
                    .streamingChatLanguageModel(streamingChatLanguageModel)
                    .chatMemory(chatMemory)
                    .tools(aiBusinessTool);
            // 混合检索
            RetrievalAugmentor hybrid = hybridRetrievalAugmentor.getIfAvailable();
            // 如果混合检索可用，则使用混合检索
            if (hybrid != null) {
                builder.retrievalAugmentor(hybrid);
            } else {
                builder.contentRetriever(manualGovernedRagRetriever);
            }
            return builder.build();
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

    /** 登录用户维度隔离的对话记忆（最多窗口条数见 withMaxMessages）。 */
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
