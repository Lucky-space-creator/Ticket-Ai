package com.ticket.train.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI trainServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("train-service")
                        .description("车次与库存域 API（含订单域 Feign 调用的 internal 路径）")
                        .version("2.0.0"));
    }
}
