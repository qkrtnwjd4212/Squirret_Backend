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
    private final FastApiSessionService fastApiSessionService;
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public InferenceSessionService(JwtService jwtService, FastApiSessionService fastApiSessionService) {
        this.jwtService = jwtService;
        this.fastApiSessionService = fastApiSessionService;
    }

    /**
     * 세션 생성 (FastAPI 세션과 매핑)
     * 
     * @param userId 사용자 ID
     * @param side 분석 방향 ("auto", "left", "right"), null이면 "auto"
     * @return 세션 정보
     */
    public CreateSessionResponse createSession(String userId, String side) {
        // Spring 세션 ID 생성
        String springSessionId = UUID.randomUUID().toString();
        
        // FastAPI 세션 생성
        String fastApiSessionId = fastApiSessionService.createFastApiSession(side);
        
        // Spring 세션 정보에 FastAPI 세션 ID 저장
        SessionInfo info = new SessionInfo(springSessionId, userId, Instant.now(), SessionStatus.ACTIVE);
        info.setFastApiSessionId(fastApiSessionId);
        sessions.put(springSessionId, info);

        log.info("세션 생성: springSessionId={}, fastApiSessionId={}, userId={}, side={}", 
            springSessionId, fastApiSessionId, userId, side);
        
        // FastAPI는 REST API이므로 wsUrl은 FastAPI base URL로 설정
        String fastApiUrl = fastApiSessionService.getFastApiBaseUrl();
        
        return new CreateSessionResponse(
            springSessionId, 
            fastApiUrl, 
            fastApiSessionId  // wsToken 대신 fastApiSessionId 반환
        );
    }
    
    /**
     * 세션 생성 (기존 메서드 호환성)
     */
    public CreateSessionResponse createSession(String userId) {
        return createSession(userId, "auto");
    }
    
    /**
     * FastAPI 세션 ID로 Spring 세션 ID 조회
     */
    public String getSpringSessionIdByFastApiSessionId(String fastApiSessionId) {
        return sessions.values().stream()
                .filter(info -> fastApiSessionId.equals(info.getFastApiSessionId()))
                .map(SessionInfo::getSessionId)
                .findFirst()
                .orElse(null);
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

    /**
     * sessionId로 userId 조회
     * @param sessionId 세션 ID
     * @return userId (세션이 없거나 만료된 경우 null)
     */
    public String getUserIdBySessionId(String sessionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info == null) {
            log.warn("세션을 찾을 수 없음: sessionId={}", sessionId);
            return null;
        }
        // 만료 확인
        if (Instant.now().isAfter(info.getCreatedAt().plus(SESSION_TTL))) {
            sessions.remove(sessionId);
            log.warn("세션이 만료됨: sessionId={}", sessionId);
            return null;
        }
        return info.getUserId();
    }

    /**
     * 세션 생성 응답
     * - sessionId: Spring 세션 ID
     * - fastApiUrl: FastAPI base URL (앱이 직접 접근)
     * - fastApiSessionId: FastAPI 세션 ID (앱이 프레임 업로드 시 사용)
     */
    public record CreateSessionResponse(String sessionId, String fastApiUrl, String fastApiSessionId) {}
    public record RefreshTokenResponse(String sessionId, String wsToken) {}
    public record SessionStats(Integer framesIn, Integer framesOut, Long durationSeconds) {}

    private static class SessionInfo {
        private final String sessionId;
        private final String userId;
        private final Instant createdAt;
        private SessionStatus status;
        private SessionStats stats;
        private String fastApiSessionId;  // FastAPI 세션 ID

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
        public String getFastApiSessionId() { return fastApiSessionId; }
        public void setFastApiSessionId(String fastApiSessionId) { this.fastApiSessionId = fastApiSessionId; }
    }

    private enum SessionStatus {
        ACTIVE, COMPLETED, EXPIRED
    }
}

