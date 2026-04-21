package com.ticket.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Slf4j
@Getter
@Configuration
public class AIModelConfig {

    @Value("${ai.model-type}")
    private String modelType;
    
    @Value("${ai.enabled}")
    private boolean aiEnabled;
    
    @Value("${ai.fallback.enabled}")
    private boolean fallbackEnabled;
    
    @Value("${ai.fallback.fallback-type}")
    private String fallbackType;
    
    // Ollama配置
    @Value("${ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${ai.ollama.chat-model}")
    private String ollamaChatModelName;

    @Value("${ai.ollama.embedding-model}")
    private String ollamaEmbeddingModelName;

    @Value("${ai.ollama.temperature}")
    private Double ollamaTemperature;

    @Value("${ai.ollama.timeout}")
    private int ollamaTimeoutSeconds;
    
//    // OpenAI配置
//    @Value("${ai.openai.api-key}")
//    private String openaiApiKey;
//
//    @Value("${ai.openai.model}")
//    private String openaiModel;
//
//    @Value("${ai.openai.temperature}")
//    private Double openaiTemperature;
//
//    @Value("${ai.openai.timeout}")
//    private int openaiTimeoutSeconds;
    
    // HTTP API配置
    @Value("${ai.http-api.base-url}")
    private String httpApiBaseUrl;

    @Value("${ai.http-api.api-key}")
    private String httpApiKey;

    @Value("${ai.http-api.model}")
    private String httpApiModel;

    @Value("${ai.http-api.temperature}")
    private Double httpApiTemperature;

    @Value("${ai.http-api.timeout}")
    private int httpApiTimeoutSeconds;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (!aiEnabled) {
            log.info("=== AI服务已禁用 ===");
            return null;
        }
        
        log.info("=== 初始化AI对话模型 ===");
        log.info("模型类型: {}", modelType);
        
        try {
            switch (modelType.toLowerCase()) {
                case "ollama":
                    log.info("尝试连接Ollama服务: {}", ollamaBaseUrl);
                    log.info("模型名称: {}", ollamaChatModelName);
                    return createOllamaChatModel();
                    
                case "http-api":
                    log.info("使用HTTP API");
                    log.info("API地址: {}", httpApiBaseUrl);
                    return createHttpApiChatModel();
                    
                default:
                    log.warn("未知的模型类型: {}, 使用默认的Ollama配置", modelType);
                    return createOllamaChatModel();
            }
        } catch (Exception e) {
            log.error("初始化主模型失败: {}", e.getMessage(), e);
            
            // 如果启用了回退机制，尝试使用备用模式
            if (fallbackEnabled) {
                log.info("尝试使用备用模式: {}", fallbackType);
                try {
                    if (fallbackType.equalsIgnoreCase("http-api")) {
                        return createHttpApiChatModel();
                    } else {
                        log.warn("未知的备用模型类型: {}", fallbackType);
                    }
                } catch (Exception ex) {
                    log.error("备用模式也失败: {}", ex.getMessage(), ex);
                }
            }

            log.warn("所有AI模型初始化失败，使用虚拟模型");
            return null;
        }
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        if (!aiEnabled) {
            log.info("=== AI向量化模型已禁用 ===");
            return null;
        }
        
        log.info("=== 初始化AI向量化模型 ===");
        log.info("模型类型: {}", modelType);
        
        try {
            switch (modelType.toLowerCase()) {
                case "ollama":
                    log.info("Ollama服务地址: {}", ollamaBaseUrl);
                    log.info("嵌入模型名称: {}", ollamaEmbeddingModelName);
                    return OllamaEmbeddingModel.builder()
                            .baseUrl(ollamaBaseUrl)
                            .modelName(ollamaEmbeddingModelName)
                            .timeout(Duration.ofSeconds(ollamaTimeoutSeconds))
                            .build();
                            
                case "openai":
                case "http-api":
                    // OpenAI和HTTP API使用相同的嵌入模型
                    log.info("使用OpenAI兼容的嵌入模型");
                    return OpenAiEmbeddingModel.builder()
                            .apiKey(getEffectiveApiKey())
                            .modelName("text-embedding-ada-002")
                            .timeout(Duration.ofSeconds(httpApiTimeoutSeconds))
                            .build();

                default:
                    log.warn("未知的模型类型: {}, 使用默认的Ollama嵌入模型", modelType);
                    return OllamaEmbeddingModel.builder()
                            .baseUrl(ollamaBaseUrl)
                            .modelName(ollamaEmbeddingModelName)
                            .timeout(Duration.ofSeconds(ollamaTimeoutSeconds))
                            .build();
            }
        } catch (Exception e) {
            log.error("初始化向量化模型失败: {}", e.getMessage(), e);

            // 如果启用了回退机制
            if (fallbackEnabled) {
                try {
                    log.info("尝试使用备用向量化模型");
                    return OpenAiEmbeddingModel.builder()
                            .apiKey(getEffectiveApiKey())
                            .modelName("text-embedding-ada-002")
                            .timeout(Duration.ofSeconds(httpApiTimeoutSeconds))
                            .build();
                } catch (Exception ex) {
                    log.error("备用向量化模型也失败: {}", ex.getMessage(), ex);
                }
            }

            log.warn("所有向量化模型初始化失败，使用虚拟模型");
            return null;
        }
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        if (!aiEnabled) {
            log.info("=== AI流式模型已禁用 ===");
            return null;
        }

        log.info("=== 初始化AI流式对话模型 ===");
        log.info("模型类型: {}", modelType);

        try {
            switch (modelType.toLowerCase()) {
                case "ollama":
                    log.info("尝试连接Ollama流式服务: {}", ollamaBaseUrl);
                    log.info("模型名称: {}", ollamaChatModelName);
                    return OllamaStreamingChatModel.builder()
                            .baseUrl(ollamaBaseUrl)
                            .modelName(ollamaChatModelName)
                            .temperature(ollamaTemperature)
                            .timeout(Duration.ofSeconds(ollamaTimeoutSeconds))
                            .build();

                case "http-api":
                    log.info("使用HTTP API流式模型");
                    log.info("API地址: {}", httpApiBaseUrl);
                    return OpenAiStreamingChatModel.builder()
                            .apiKey(httpApiKey)
                            .baseUrl(httpApiBaseUrl)
                            .modelName(httpApiModel)
                            .temperature(httpApiTemperature)
                            .timeout(Duration.ofSeconds(httpApiTimeoutSeconds))
                            .build();

                default:
                    log.warn("未知的模型类型: {}, 使用默认的Ollama流式配置", modelType);
                    return OllamaStreamingChatModel.builder()
                            .baseUrl(ollamaBaseUrl)
                            .modelName(ollamaChatModelName)
                            .temperature(ollamaTemperature)
                            .timeout(Duration.ofSeconds(ollamaTimeoutSeconds))
                            .build();
            }
        } catch (Exception e) {
            log.error("初始化流式模型失败: {}", e.getMessage(), e);

            // 如果启用了回退机制
            if (fallbackEnabled) {
                log.info("尝试使用备用流式模式: {}", fallbackType);
                try {
                    if (fallbackType.equalsIgnoreCase("http-api")) {
                        return OpenAiStreamingChatModel.builder()
                                .apiKey(httpApiKey)
                                .baseUrl(httpApiBaseUrl)
                                .modelName(httpApiModel)
                                .temperature(httpApiTemperature)
                                .timeout(Duration.ofSeconds(httpApiTimeoutSeconds))
                                .build();
                    } else {
                        log.warn("未知的备用流式模型类型: {}", fallbackType);
                    }
                } catch (Exception ex) {
                    log.error("备用流式模式也失败: {}", ex.getMessage(), ex);
                }
            }

            log.warn("所有流式AI模型初始化失败，使用虚拟模型");
            return null;
        }
    }

    private ChatLanguageModel createOllamaChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaChatModelName)
                .temperature(ollamaTemperature)
                .timeout(Duration.ofSeconds(ollamaTimeoutSeconds))
                .build();
    }


    private ChatLanguageModel createHttpApiChatModel() {
        String apiKey = httpApiKey;
        String baseUrl = httpApiBaseUrl;

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new RuntimeException("HTTP API密钥未配置");
        }

        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new RuntimeException("HTTP API基础地址未配置");
        }

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(httpApiModel)
                .temperature(httpApiTemperature)
                .timeout(Duration.ofSeconds(httpApiTimeoutSeconds))
                .build();
    }


    private String getEffectiveApiKey() {
        // 返回有效的API密钥，修复空指针异常

        if ("http-api".equalsIgnoreCase(modelType)) {
            if (httpApiKey != null && !httpApiKey.trim().isEmpty()) {
                return httpApiKey.trim();
            }
        }

        // 回退逻辑
        if (fallbackEnabled) {
            if ("http-api".equalsIgnoreCase(fallbackType)) {
                if (httpApiKey != null && !httpApiKey.trim().isEmpty()) {
                    return httpApiKey.trim();
                }
            }
            if (httpApiKey != null && !httpApiKey.trim().isEmpty()) {
                return httpApiKey.trim();
            }
        }

        throw new RuntimeException("未找到有效的API密钥。请配置ai.openai.api-key或ai.http-api.api-key环境变量");
    }
}