package com.ticket.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AIModelConfig {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.chat-model:qwen2:7b}")
    private String chatModelName;

    @Value("${ollama.embedding-model:nomic-embed-text}")
    private String embeddingModelName;

    @Value("${ollama.temperature:0.7}")
    private Double temperature;

    @Value("${ollama.timeout:60}")
    private int timeoutSeconds;  // 改为 int 类型，单位秒

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        System.out.println("=== 初始化 Ollama 对话模型 ===");
        System.out.println("服务地址: " + ollamaBaseUrl);
        System.out.println("模型名称: " + chatModelName);

        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(timeoutSeconds))  // 使用 Duration.ofSeconds()
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        System.out.println("=== 初始化 Ollama 嵌入模型 ===");
        System.out.println("服务地址: " + ollamaBaseUrl);
        System.out.println("模型名称: " + embeddingModelName);

        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .build();
    }
}