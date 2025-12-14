package com.squirret.squirretbackend.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.service.FSRDataService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class FSRWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FSRDataService fsrDataService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        log.info("웹소켓 연결됨: {}", session.getId());
        
        // 연결 시 최신 데이터 즉시 전송 (깔창이 연결되지 않아도 모든 센서가 0으로 표시됨)
        try {
            Map<String, FSRDataDTO> latestData = fsrDataService.getLatestInsoleData(true);
            log.info("최신 데이터 조회 완료: left={}, right={}", 
                    latestData.get("left") != null ? "있음" : "없음",
                    latestData.get("right") != null ? "있음" : "없음");
            
            String jsonMessage = objectMapper.writeValueAsString(latestData);
            log.info("JSON 변환 완료, 메시지 길이: {} bytes", jsonMessage.length());
            log.info("전송할 JSON 내용: {}", jsonMessage);
            
            TextMessage message = new TextMessage(jsonMessage);
            if (session.isOpen()) {
                session.sendMessage(message);
                log.info("연결 시 최신 데이터 전송 완료: {}", session.getId());
            } else {
                log.warn("세션이 이미 닫혀있음: {}", session.getId());
            }
        } catch (Exception e) {
            log.error("연결 시 데이터 전송 실패: {}", session.getId(), e);
            e.printStackTrace();
        }
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

