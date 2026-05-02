package com.ticket.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI adminServiceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("admin-service").description("管理端 API").version("2.0.0"));
    }
}
