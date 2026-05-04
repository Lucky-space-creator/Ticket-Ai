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
 * ChromaDB 向量存储（仅 aichat-service 使用）
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
            log.warn("ChromaDB 不可用，降级为内存向量库: {}", ex.getMessage());
            return new InMemoryEmbeddingStore<>();
        }
    }
}
