package com.lingshu.ai.web.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
            new WebSocketHandlerDecorator(chatWebSocketHandler) {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    session.setTextMessageSizeLimit(10 * 1024 * 1024);
                    session.setBinaryMessageSizeLimit(10 * 1024 * 1024);
                    super.afterConnectionEstablished(session);
                }
            },
            "/ws/chat"
        ).setAllowedOrigins(
            "http://localhost:5173",
            "tauri://localhost",
            "http://tauri.localhost"
        );
    }
}
