package com.squirret.squirretbackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squirret.squirretbackend.dto.FastApiWebSocketResponse;
import com.squirret.squirretbackend.dto.InferenceFeedbackDto;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastAPI WebSocket 클라이언트
 * 세션별로 WebSocket 연결을 관리하고 FastAPI에서 보내는 상태/피드백을 수신합니다.
 * (프레임은 앱에서 FastAPI로 직접 전송)
 */
@Slf4j
@Component
public class FastApiWebSocketClient {

    @Value("${fastapi.base-url:https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io}")
    private String fastApiBaseUrl;

    private final ObjectMapper objectMapper;
    private final InferenceFeedbackService inferenceFeedbackService;
    
    // 세션별 WebSocket 클라이언트 관리
    private final Map<String, WebSocketClient> sessionClients = new ConcurrentHashMap<>();
    
    public FastApiWebSocketClient(InferenceFeedbackService inferenceFeedbackService) {
        this.objectMapper = new ObjectMapper();
        this.inferenceFeedbackService = inferenceFeedbackService;
    }

    /**
     * FastAPI WebSocket에 연결
     * 
     * @param sessionId Spring 세션 ID
     * @param fastApiSessionId FastAPI 세션 ID
     * @return 연결 성공 여부
     */
    public boolean connect(String sessionId, String fastApiSessionId) {
        // 이미 연결되어 있으면 재연결
        if (sessionClients.containsKey(sessionId)) {
            disconnect(sessionId);
        }

        try {
            // WebSocket URL 구성 (https -> wss 변환)
            String wsUrl = fastApiBaseUrl
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")
                    + "/ws/" + fastApiSessionId;
            
            URI uri = new URI(wsUrl);
            log.info("FastAPI WebSocket 연결 시도: sessionId={}, fastApiSessionId={}, url={}", 
                sessionId, fastApiSessionId, wsUrl);

            WebSocketClient client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("FastAPI WebSocket 연결 성공: sessionId={}, fastApiSessionId={}, status={}", 
                        sessionId, fastApiSessionId, handshake.getHttpStatus());
                }

                @Override
                public void onMessage(String message) {
                    handleWebSocketMessage(sessionId, message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("FastAPI WebSocket 연결 종료: sessionId={}, fastApiSessionId={}, code={}, reason={}, remote={}", 
                        sessionId, fastApiSessionId, code, reason, remote);
                    sessionClients.remove(sessionId);
                }

                @Override
                public void onError(Exception ex) {
                    log.error("FastAPI WebSocket 오류: sessionId={}, fastApiSessionId={}", 
                        sessionId, fastApiSessionId, ex);
                }
            };

            client.connect();
            sessionClients.put(sessionId, client);
            
            // 연결 대기 (최대 5초)
            int retries = 0;
            while (!client.isOpen() && retries < 50) {
                Thread.sleep(100);
                retries++;
            }
            
            if (client.isOpen()) {
                log.info("FastAPI WebSocket 연결 완료: sessionId={}, fastApiSessionId={}", 
                    sessionId, fastApiSessionId);
                return true;
            } else {
                log.error("FastAPI WebSocket 연결 실패: sessionId={}, fastApiSessionId={}", 
                    sessionId, fastApiSessionId);
                sessionClients.remove(sessionId);
                return false;
            }
        } catch (Exception e) {
            log.error("FastAPI WebSocket 연결 오류: sessionId={}, fastApiSessionId={}", 
                sessionId, fastApiSessionId, e);
            sessionClients.remove(sessionId);
            return false;
        }
    }

    /**
     * WebSocket 메시지 처리
     * FastAPI 응답을 InferenceFeedbackDto로 변환하여 InferenceFeedbackService로 전달
     */
    private void handleWebSocketMessage(String sessionId, String message) {
        try {
            log.debug("FastAPI WebSocket 메시지 수신: sessionId={}, message={}", sessionId, message);
            
            // JSON 파싱
            FastApiWebSocketResponse response = objectMapper.readValue(message, FastApiWebSocketResponse.class);
            
            // 에러 응답 처리
            if (response.getStatus() != null && response.getStatus() >= 400) {
                log.warn("FastAPI WebSocket 에러 응답: sessionId={}, status={}, code={}, message={}", 
                    sessionId, response.getStatus(), response.getCode(), response.getMessage());
                return;
            }
            
            // InferenceFeedbackDto로 변환
            InferenceFeedbackDto feedback = convertToInferenceFeedback(response);
            
            // InferenceFeedbackService로 전달
            inferenceFeedbackService.sendFeedbackToApp(sessionId, feedback);
            
        } catch (Exception e) {
            log.error("FastAPI WebSocket 메시지 처리 오류: sessionId={}, message={}", 
                sessionId, message, e);
        }
    }

    /**
     * FastApiWebSocketResponse를 InferenceFeedbackDto로 변환
     */
    private InferenceFeedbackDto convertToInferenceFeedback(FastApiWebSocketResponse response) {
        InferenceFeedbackDto.InferenceFeedbackDtoBuilder builder = InferenceFeedbackDto.builder()
                .type("analysis")
                .state(response.getState())
                .side(response.getSide())
                .squatCount(response.getSquatCount())
                .timestamp(System.currentTimeMillis());
        
        // checks 변환 (FastAPI: "Good"/"Bad"/null -> 기존 형식: "good"/"bad"/null)
        if (response.getChecks() != null) {
            Map<String, String> checks = new HashMap<>();
            for (Map.Entry<String, String> entry : response.getChecks().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value != null) {
                    // "Good" -> "good", "Bad" -> "bad"
                    checks.put(key, value.toLowerCase());
                }
            }
            builder.checks(checks);
        }
        
        // score 변환 (Double -> Integer)
        if (response.getScore() != null) {
            builder.score(response.getScore().intValue());
        }
        
        // avg_score는 별도로 처리하지 않음 (필요시 추가 가능)
        
        return builder.build();
    }


    /**
     * WebSocket 연결 종료
     * 
     * @param sessionId Spring 세션 ID
     */
    public void disconnect(String sessionId) {
        WebSocketClient client = sessionClients.remove(sessionId);
        if (client != null) {
            try {
                if (client.isOpen()) {
                    client.close();
                }
                log.info("FastAPI WebSocket 연결 종료: sessionId={}", sessionId);
            } catch (Exception e) {
                log.error("FastAPI WebSocket 연결 종료 오류: sessionId={}", sessionId, e);
            }
        }
    }

    /**
     * 연결 상태 확인
     * 
     * @param sessionId Spring 세션 ID
     * @return 연결 여부
     */
    public boolean isConnected(String sessionId) {
        WebSocketClient client = sessionClients.get(sessionId);
        return client != null && client.isOpen();
    }
}
