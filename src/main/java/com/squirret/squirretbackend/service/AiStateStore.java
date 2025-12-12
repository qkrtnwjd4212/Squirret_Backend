package com.squirret.squirretbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * AI 상태를 세션별(사용자별)로 관리하는 스토어
 * 전역 상태 문제를 해결하기 위해 userId 기반으로 분리 관리
 */
@Slf4j
@Component
public class AiStateStore {

    // userId -> AI 상태 맵 (세션별 분리)
    private final Map<String, UserAiState> userStates = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 특정 사용자의 AI 상태 업데이트
     * @param userId 사용자 ID (게스트 ID 또는 세션 ID)
     * @param lumbar 허리 상태
     * @param knee 무릎 상태
     * @param ankle 발목 상태
     */
    public void update(String userId, String lumbar, String knee, String ankle) {
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("⚠️ userId가 null이거나 비어있어 AI 상태 업데이트를 건너뜁니다.");
            return;
        }

        lock.writeLock().lock();
        try {
            UserAiState state = userStates.computeIfAbsent(userId, k -> new UserAiState());
            Map<String, String> m = new HashMap<>();
            if (lumbar != null) m.put("lumbar", lumbar);
            if (knee != null) m.put("knee", knee);
            if (ankle != null) m.put("ankle", ankle);
            state.latest = m;
            state.lastUpdateTime = System.currentTimeMillis();
            
            log.info("✅ AI 상태 업데이트: userId={}, state={}, timestamp={}", 
                userId, m, state.lastUpdateTime);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 특정 사용자의 AI 상태 스냅샷 조회
     * @param userId 사용자 ID
     * @return AI 상태 맵 (없으면 빈 맵 반환)
     */
    public Map<String, String> snapshot(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("⚠️ userId가 null이거나 비어있어 빈 상태를 반환합니다.");
            return Collections.emptyMap();
        }

        lock.readLock().lock();
        try {
            UserAiState state = userStates.get(userId);
            if (state == null || state.latest == null) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(new HashMap<>(state.latest));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 전역 상태 조회 (하위 호환성 유지, 첫 번째 사용자 상태 반환)
     * @deprecated 세션별 관리로 전환되었으므로 가능하면 snapshot(userId) 사용 권장
     */
    @Deprecated
    public Map<String, String> snapshot() {
        lock.readLock().lock();
        try {
            if (userStates.isEmpty()) {
                return Collections.emptyMap();
            }
            // 첫 번째 사용자 상태 반환 (하위 호환성)
            UserAiState firstState = userStates.values().iterator().next();
            if (firstState.latest == null) {
                return Collections.emptyMap();
            }
            log.warn("⚠️ 전역 snapshot() 호출됨. 세션별 snapshot(userId) 사용을 권장합니다.");
            return Collections.unmodifiableMap(new HashMap<>(firstState.latest));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 사용자 상태 제거 (세션 종료 시 호출)
     * @param userId 사용자 ID
     */
    public void remove(String userId) {
        if (userId == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            userStates.remove(userId);
            log.debug("AI 상태 제거: userId={}", userId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 사용자별 AI 상태 저장 클래스
     */
    private static class UserAiState {
        Map<String, String> latest = new HashMap<>();
        long lastUpdateTime = System.currentTimeMillis();
    }
}


