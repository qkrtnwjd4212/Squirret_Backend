package com.squirret.squirretbackend.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자별 마지막으로 전송된 피드백 문구를 추적하는 서비스.
 * - WebSocket voice 피드백이 여러 곳에서 생성되더라도
 *   여기서 마지막 문구를 공통으로 관리해서 중복 전송을 막는다.
 */
@Service
public class FeedbackHistoryService {

    // key: userId (또는 WebSocket user), value: 마지막으로 전송된 피드백 텍스트
    private final Map<String, String> lastFeedbackByUser = new ConcurrentHashMap<>();

    // key: userId, value: (text -> lastSentEpochMillis)
    private final Map<String, Map<String, Long>> perUserCooldownMap = new ConcurrentHashMap<>();

    public String getLastFeedback(String userId) {
        if (userId == null) {
            return null;
        }
        return lastFeedbackByUser.get(userId);
    }

    public void setLastFeedback(String userId, String text) {
        if (userId == null || text == null) {
            return;
        }
        lastFeedbackByUser.put(userId, text);
    }

    /**
     * 특정 사용자에 대해, 동일한 텍스트가 cooldownMillis 내에 이미 전송되었는지 여부
     */
    public boolean isUnderCooldown(String userId, String text, long cooldownMillis) {
        if (userId == null || text == null) {
            return false;
        }
        Map<String, Long> userMap = perUserCooldownMap.get(userId);
        if (userMap == null) {
            return false;
        }
        Long lastSent = userMap.get(text);
        if (lastSent == null) {
            return false;
        }
        long now = Instant.now().toEpochMilli();
        return now - lastSent < cooldownMillis;
    }

    /**
     * 특정 사용자에 대해 텍스트가 전송되었음을 기록 (마지막 전송 시각 업데이트)
     */
    public void markSent(String userId, String text) {
        if (userId == null || text == null) {
            return;
        }
        perUserCooldownMap
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(text, Instant.now().toEpochMilli());
        // 마지막 문장도 함께 업데이트
        lastFeedbackByUser.put(userId, text);
    }
}


