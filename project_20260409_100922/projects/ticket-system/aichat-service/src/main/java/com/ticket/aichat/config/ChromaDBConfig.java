package com.ticket.aichat.config;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * ChromaDB 配置
 */
@Configuration
public class ChromaDBConfig {

    @Value("${chroma.host}")
    private String host;

    @Value("${chroma.port}")
    private int port;

    @Value("${chroma.collection}")
    private String collectionName;

    @Bean
    public ChromaEmbeddingStore chromaEmbeddingStore() {
        String baseUrl = String.format("http://%s:%d", host, port);
        System.out.println("连接 ChromaDB: " + baseUrl);

        return ChromaEmbeddingStore.builder()
                .baseUrl(baseUrl)  // ChromaDB 服务地址
                .collectionName(collectionName)  // 集合名称（类似表名）
                .build();
    }
}