package com.squirret.squirretbackend.service;

import lombok.extern.slf4j.Slf4j;
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

    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public InferenceSessionService() {
    }

    /**
     * 프론트에서 발급받은 FastAPI 세션을 백엔드에 등록
     * 
     * @param userId 사용자 ID (게스트 ID)
     * @param fastApiSessionId 프론트에서 FastAPI로부터 발급받은 세션 ID
     * @return Spring 세션 정보
     */
    public CreateSessionResponse registerFastApiSession(String userId, String fastApiSessionId) {
        if (fastApiSessionId == null || fastApiSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("FastAPI 세션 ID가 필요합니다.");
        }
        
        // Spring 세션 ID 생성 (백엔드 내부 관리용)
        String springSessionId = UUID.randomUUID().toString();
        
        // Spring 세션 정보에 FastAPI 세션 ID 저장
        SessionInfo info = new SessionInfo(springSessionId, userId, Instant.now(), SessionStatus.ACTIVE);
        info.setFastApiSessionId(fastApiSessionId);
        sessions.put(springSessionId, info);

        log.info("FastAPI 세션 등록: springSessionId={}, fastApiSessionId={}, userId={}", 
            springSessionId, fastApiSessionId, userId);
        
        return new CreateSessionResponse(
            springSessionId, 
            null,  // fastApiUrl은 더 이상 필요 없음 (프론트가 직접 관리)
            fastApiSessionId
        );
    }
    

    /**
     * 세션 조회 (FastAPI 세션 ID로)
     * 
     * @param fastApiSessionId FastAPI 세션 ID
     * @return Spring 세션 ID (없으면 null)
     */
    public String getSpringSessionIdByFastApiSessionId(String fastApiSessionId) {
        return sessions.values().stream()
                .filter(info -> fastApiSessionId.equals(info.getFastApiSessionId()))
                .map(SessionInfo::getSessionId)
                .findFirst()
                .orElse(null);
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
     * - sessionId: Spring 세션 ID (백엔드 내부 관리용)
     * - fastApiUrl: null (더 이상 사용하지 않음)
     * - fastApiSessionId: FastAPI 세션 ID (프론트에서 발급받은 것)
     */
    public record CreateSessionResponse(String sessionId, String fastApiUrl, String fastApiSessionId) {}
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

