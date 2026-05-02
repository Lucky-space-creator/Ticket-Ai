package com.ticket.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("order-service")
                        .description("订单域 HTTP 契约（与 Feign 消费者协同演进，字段变更须向后兼容）")
                        .version("2.0.0"));
    }
}
