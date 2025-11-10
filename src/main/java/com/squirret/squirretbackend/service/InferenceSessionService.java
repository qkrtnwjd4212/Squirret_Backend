package com.squirret.squirretbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InferenceSessionService {

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration WS_TOKEN_TTL = Duration.ofMinutes(15);

    @Value("${inference.ws.base-url:ws://localhost:8000}")
    private String inferenceWsBaseUrl;

    private final JwtService jwtService;
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public InferenceSessionService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public CreateSessionResponse createSession(String userId) {
        String sessionId = UUID.randomUUID().toString();
        String wsToken = jwtService.generateInferenceWsToken(sessionId, WS_TOKEN_TTL.toMillis());
        String wsUrl = inferenceWsBaseUrl + "/ws/" + sessionId;

        SessionInfo info = new SessionInfo(sessionId, userId, Instant.now(), SessionStatus.ACTIVE);
        sessions.put(sessionId, info);

        log.info("세션 생성: sessionId={}, userId={}", sessionId, userId);
        return new CreateSessionResponse(sessionId, wsUrl, wsToken);
    }

    public RefreshTokenResponse refreshToken(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info == null || info.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("세션을 찾을 수 없거나 비활성화되었습니다: " + sessionId);
        }

        // 세션 만료 확인
        if (Instant.now().isAfter(info.getCreatedAt().plus(SESSION_TTL))) {
            sessions.remove(sessionId);
            throw new IllegalArgumentException("세션이 만료되었습니다: " + sessionId);
        }

        String wsToken = jwtService.generateInferenceWsToken(sessionId, WS_TOKEN_TTL.toMillis());
        log.info("토큰 갱신: sessionId={}", sessionId);
        return new RefreshTokenResponse(sessionId, wsToken);
    }

    public void finishSession(String sessionId, SessionStats stats) {
        SessionInfo info = sessions.get(sessionId);
        if (info == null) {
            log.warn("완료 요청한 세션을 찾을 수 없음: sessionId={}", sessionId);
            return;
        }

        info.setStatus(SessionStatus.COMPLETED);
        info.setStats(stats);
        log.info("세션 완료: sessionId={}, stats={}", sessionId, stats);
        
        // 세션은 TTL 후 자동 삭제되도록 유지 (나중에 Redis로 이동 시 TTL 활용)
    }

    public boolean isSessionActive(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info == null) {
            return false;
        }
        // 만료 확인
        if (Instant.now().isAfter(info.getCreatedAt().plus(SESSION_TTL))) {
            sessions.remove(sessionId);
            return false;
        }
        return info.getStatus() == SessionStatus.ACTIVE;
    }

    public record CreateSessionResponse(String sessionId, String wsUrl, String wsToken) {}
    public record RefreshTokenResponse(String sessionId, String wsToken) {}
    public record SessionStats(Integer framesIn, Integer framesOut, Long durationSeconds) {}

    private static class SessionInfo {
        private final String sessionId;
        private final String userId;
        private final Instant createdAt;
        private SessionStatus status;
        private SessionStats stats;

        public SessionInfo(String sessionId, String userId, Instant createdAt, SessionStatus status) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.createdAt = createdAt;
            this.status = status;
        }

        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public Instant getCreatedAt() { return createdAt; }
        public SessionStatus getStatus() { return status; }
        public void setStatus(SessionStatus status) { this.status = status; }
        public SessionStats getStats() { return stats; }
        public void setStats(SessionStats stats) { this.stats = stats; }
    }

    private enum SessionStatus {
        ACTIVE, COMPLETED, EXPIRED
    }
}

