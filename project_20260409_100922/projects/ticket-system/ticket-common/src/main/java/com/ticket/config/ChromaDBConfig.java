package com.ticket.config;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * ChromaDB 配置
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
    public EmbeddingStore<?> chromaEmbeddingStore() {
        String baseUrl = String.format("http://%s:%d", host, port);
        log.info("连接 ChromaDB: {}", baseUrl);
        try {
            return ChromaEmbeddingStore.builder()
                    .baseUrl(baseUrl)  // ChromaDB 服务地址
                    .collectionName(collectionName)  // 集合名称（类似表名）
                    .build();
        } catch (Exception ex) {
            log.warn("ChromaDB 不可用，降级为内存向量库: {}", ex.getMessage());
            return new InMemoryEmbeddingStore<>();
        }
    }
}