package com.ticket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.ticket.handler.ChatWebSocketHandler;

import jakarta.annotation.Resource;

/**
 * WebSocket 配置类
 * 注册 WebSocket 处理器，配置连接路径
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册处理器，指定路径为 /ws/chat/{sessionId}，支持跨域
        registry.addHandler(chatWebSocketHandler, "/ws/chat/{sessionId}")
                .setAllowedOriginPatterns("*");
    }
}
