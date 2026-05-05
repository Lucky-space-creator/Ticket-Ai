package com.ticket.aichat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChromaDB 向量存储（仅 aichat-service 使用）。
 * 生产环境建议 chroma.allow-in-memory-fallback=false。
 */
@Configuration
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class ChromaDBConfig {

    private static final Logger log = LoggerFactory.getLogger(ChromaDBConfig.class);

    @Value("${chroma.host:192.168.48.135}")
    private String host;

    @Value("${chroma.port:8000}")
    private int port;

    @Value("${chroma.collection:ticket_knowledge}")
    private String collectionName;

    @Value("${chroma.allow-in-memory-fallback:true}")
    private boolean allowInMemoryFallback;

    /**
     * LangChain4j EmbeddingStore：在线 Chroma，失败时可降级内存索引（仅限开发调试）。
     */
    @Bean
    public EmbeddingStore<TextSegment> chromaEmbeddingStore() {
        String baseUrl = String.format("http://%s:%d", host, port);
        log.info("连接 ChromaDB: {}", baseUrl);
        try {
            return ChromaEmbeddingStore.builder()
                    .baseUrl(baseUrl)
                    .collectionName(collectionName)
                    .build();
        } catch (Exception ex) {
            if (!allowInMemoryFallback) {
                log.error("ChromaDB 连接失败且已禁止降级内存向量库: {}", baseUrl);
                throw new IllegalStateException("无法连接 ChromaDB 且 chroma.allow-in-memory-fallback=false", ex);
            }
            log.error("ChromaDB 不可用，降级为内存向量库（仅限开发）：{}", ex.toString());
            return new InMemoryEmbeddingStore<>();
        }
    }
}
