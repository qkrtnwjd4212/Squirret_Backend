package com.squirret.squirretbackend.config;

import com.squirret.squirretbackend.handler.FSRWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final FSRWebSocketHandler fsrWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 웹소켓 엔드포인트: ws://localhost:8080/ws/fsr-data
        registry.addHandler(fsrWebSocketHandler, "/ws/fsr-data")
                .setAllowedOrigins("*"); // CORS 설정 (프로덕션에서는 특정 도메인으로 제한)
    }
}

