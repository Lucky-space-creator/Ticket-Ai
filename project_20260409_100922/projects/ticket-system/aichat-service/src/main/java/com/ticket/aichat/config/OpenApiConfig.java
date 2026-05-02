package com.ticket.aichat.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aichatServiceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("aichat-service").description("AI 对话与知识库 API").version("2.0.0"));
    }
}
