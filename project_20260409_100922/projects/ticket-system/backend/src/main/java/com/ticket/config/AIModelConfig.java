package com.ticket.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.output.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class AIModelConfig {

    @Value("${ai.model-type:ollama}")
    private String modelType;
    
    @Value("${ai.enabled:true}")
    private boolean aiEnabled;
    
    @Value("${ai.fallback.enabled:true}")
    private boolean fallbackEnabled;
    
    @Value("${ai.fallback.fallback-type:http-api}")
    private String fallbackType;
    
    // Ollama配置
    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ai.ollama.chat-model:qwen2:7b}")
    private String ollamaChatModelName;

    @Value("${ai.ollama.embedding-model:nomic-embed-text}")
    private String ollamaEmbeddingModelName;

    @Value("${ai.ollama.temperature:0.7}")
    private Double ollamaTemperature;

    @Value("${ai.ollama.timeout:60}")
    private int ollamaTimeoutSeconds;
    
    // OpenAI配置
    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.openai.model:gpt-3.5-turbo}")
    private String openaiModel;

    @Value("${ai.openai.temperature:0.7}")
    private Double openaiTemperature;

    @Value("${ai.openai.timeout:60}")
    private int openaiTimeoutSeconds;
    
    // HTTP API配置
    @Value("${ai.http-api.base-url:}")
    private String httpApiBaseUrl;

    @Value("${ai.http-api.api-key:}")
    private String httpApiKey;

    @Value("${ai.http-api.model:gpt-3.5-turbo}")
    private String httpApiModel;

    @Value("${ai.http-api.temperature:0.7}")
    private Double httpApiTemperature;

    @Value("${ai.http-api.timeout:60}")
    private int httpApiTimeoutSeconds;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (!aiEnabled) {
            System.out.println("=== AI服务已禁用 ===");
            return null;
        }
        
        System.out.println("=== 初始化AI对话模型 ===");
        System.out.println("模型类型: " + modelType);
        
        try {
            switch (modelType.toLowerCase()) {
                case "ollama":
                    System.out.println("尝试连接Ollama服务: " + ollamaBaseUrl);
                    System.out.println("模型名称: " + ollamaChatModelName);
                    return createOllamaChatModel();
                    
                case "openai":
                    System.out.println("使用OpenAI API");
                    System.out.println("模型: " + openaiModel);
                    return createOpenAiChatModel();
                    
                case "http-api":
                    System.out.println("使用HTTP API");
                    System.out.println("API地址: " + httpApiBaseUrl);
                    return createHttpApiChatModel();
                    
                default:
                    System.err.println("未知的模型类型: " + modelType + ", 使用默认的Ollama配置");
                    return createOllamaChatModel();
            }
        } catch (Exception e) {
            System.err.println("初始化主模型失败: " + e.getMessage());
            
            // 如果启用了回退机制，尝试使用备用模式
            if (fallbackEnabled) {
                System.out.println("尝试使用备用模式: " + fallbackType);
                try {
                    switch (fallbackType.toLowerCase()) {
                        case "openai":
                            return createOpenAiChatModel();
                        case "http-api":
                            return createHttpApiChatModel();
                        default:
                            System.err.println("未知的备用模型类型: " + fallbackType);
                    }
                } catch (Exception ex) {
                    System.err.println("备用模式也失败: " + ex.getMessage());
                }
            }
            
            System.err.println("所有AI模型初始化失败，使用虚拟模型");
            return null;
        }
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        if (!aiEnabled) {
            System.out.println("=== AI向量化模型已禁用 ===");
            return null;
        }
        
        System.out.println("=== 初始化AI向量化模型 ===");
        System.out.println("模型类型: " + modelType);
        
        try {
            switch (modelType.toLowerCase()) {
                case "ollama":
                    System.out.println("Ollama服务地址: " + ollamaBaseUrl);
                    System.out.println("嵌入模型名称: " + ollamaEmbeddingModelName);
                    return OllamaEmbeddingModel.builder()
                            .baseUrl(ollamaBaseUrl)
                            .modelName(ollamaEmbeddingModelName)
                            .build();
                            
                case "openai":
                case "http-api":
                    // OpenAI和HTTP API使用相同的嵌入模型
                    System.out.println("使用OpenAI兼容的嵌入模型");
                    return OpenAiEmbeddingModel.builder()
                            .apiKey(getEffectiveApiKey())
                            .modelName("text-embedding-ada-002")
                            .build();
                            
                default:
                    System.err.println("未知的模型类型: " + modelType + ", 使用默认的Ollama嵌入模型");
                    return OllamaEmbeddingModel.builder()
                            .baseUrl(ollamaBaseUrl)
                            .modelName(ollamaEmbeddingModelName)
                            .build();
            }
        } catch (Exception e) {
            System.err.println("初始化向量化模型失败: " + e.getMessage());
            
            // 如果启用了回退机制
            if (fallbackEnabled) {
                try {
                    System.out.println("尝试使用备用向量化模型");
                    return OpenAiEmbeddingModel.builder()
                            .apiKey(getEffectiveApiKey())
                            .modelName("text-embedding-ada-002")
                            .build();
                } catch (Exception ex) {
                    System.err.println("备用向量化模型也失败: " + ex.getMessage());
                }
            }
            
            System.err.println("所有向量化模型初始化失败，使用虚拟模型");
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
    
    private ChatLanguageModel createOpenAiChatModel() {
        String apiKey = openaiApiKey;
        if (apiKey == null || apiKey.trim().isEmpty() || "sk-demo-key".equals(apiKey)) {
            throw new RuntimeException("OpenAI API密钥未配置或为默认值");
        }
        
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(openaiModel)
                .temperature(openaiTemperature)
                .timeout(Duration.ofSeconds(openaiTimeoutSeconds))
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
        if ("openai".equalsIgnoreCase(modelType)) {
            if (openaiApiKey != null && !openaiApiKey.trim().isEmpty() && !"sk-demo-key".equals(openaiApiKey)) {
                return openaiApiKey.trim();
            }
        }
        
        if ("http-api".equalsIgnoreCase(modelType)) {
            if (httpApiKey != null && !httpApiKey.trim().isEmpty()) {
                return httpApiKey.trim();
            }
        }
        
        // 回退逻辑
        if (fallbackEnabled) {
            if ("openai".equalsIgnoreCase(fallbackType)) {
                if (openaiApiKey != null && !openaiApiKey.trim().isEmpty() && !"sk-demo-key".equals(openaiApiKey)) {
                    return openaiApiKey.trim();
                }
            } else if ("http-api".equalsIgnoreCase(fallbackType)) {
                if (httpApiKey != null && !httpApiKey.trim().isEmpty()) {
                    return httpApiKey.trim();
                }
            }
            
            // 如果指定的回退类型失败，尝试其他可用的
            if (openaiApiKey != null && !openaiApiKey.trim().isEmpty() && !"sk-demo-key".equals(openaiApiKey)) {
                return openaiApiKey.trim();
            }
            
            if (httpApiKey != null && !httpApiKey.trim().isEmpty()) {
                return httpApiKey.trim();
            }
        }
        
        throw new RuntimeException("未找到有效的API密钥。请配置ai.openai.api-key或ai.http-api.api-key环境变量");
    }
}