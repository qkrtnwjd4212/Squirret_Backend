package com.squirret.squirretbackend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squirret.squirretbackend.dto.FSRDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FSRWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("웹소켓 연결됨: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        log.info("웹소켓 연결 종료: {}", session.getId());
    }

    /**
     * 모든 연결된 클라이언트에게 FSR 데이터 브로드캐스트
     */
    public void broadcastFSRData(Map<String, FSRDataDTO> data) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(data);
            TextMessage message = new TextMessage(jsonMessage);

            sessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.error("웹소켓 메시지 전송 실패: {}", session.getId(), e);
                }
            });
        } catch (Exception e) {
            log.error("FSR 데이터 브로드캐스트 실패", e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 클라이언트로부터 메시지를 받을 경우 처리 (필요시)
        log.debug("클라이언트로부터 메시지 수신: {}", message.getPayload());
    }
}

