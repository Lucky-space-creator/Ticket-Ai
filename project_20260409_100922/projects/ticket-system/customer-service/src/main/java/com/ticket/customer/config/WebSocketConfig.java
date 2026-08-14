package com.ticket.customer.config;

import com.ticket.customer.handler.ChatWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 允许跨域的源，从配置读取（逗号分隔）。默认收紧为同源，禁止 "*" 通配开放。
     */
    @Value("${ticket.customer.websocket.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isBlank())
                .toList();
        // addHandler 返回 WebSocketHandlerRegistration，拦截器与跨域均在此注册
        var registration = registry
                .addHandler(chatWebSocketHandler, "/ws/chat/{sessionId}")
                .addInterceptors(new WsAuthHandshakeInterceptor());
        if (!origins.isEmpty()) {
            registration.setAllowedOriginPatterns(origins.toArray(new String[0]));
        }
    }
}
