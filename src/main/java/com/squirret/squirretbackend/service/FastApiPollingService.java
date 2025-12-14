package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.InferenceFeedbackDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastAPI에서 주기적으로 피드백을 가져오는 서비스 (Polling 방식)
 * 
 * 현재는 FastAPI가 Spring으로 Push하는 방식만 있지만,
 * FastAPI에 피드백 조회 API가 있다면 이 서비스를 통해 주기적으로 가져올 수 있습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FastApiPollingService {

    private final FastApiSessionService fastApiSessionService;
    private final InferenceSessionService inferenceSessionService;
    private final InferenceFeedbackService inferenceFeedbackService;
    
    // 마지막으로 가져온 피드백의 타임스탬프를 저장 (중복 방지)
    private final Map<String, Long> lastFeedbackTimestamps = new ConcurrentHashMap<>();

    /**
     * 활성 세션에 대해 주기적으로 FastAPI에서 피드백 가져오기
     * 
     * 주의: FastAPI에 GET /api/session/{sessionId}/feedback 같은 엔드포인트가 있어야 작동합니다.
     * 현재는 주석 처리되어 있으며, FastAPI API가 확인되면 활성화할 수 있습니다.
     */
    // @Scheduled(fixedRate = 2000) // 2초마다 (필요에 따라 조정)
    public void pollFastApiFeedback() {
        // 활성 세션 목록 가져오기
        Set<String> activeFastApiSessions = inferenceSessionService.getActiveFastApiSessions();
        
        if (activeFastApiSessions.isEmpty()) {
            return;
        }

        log.debug("FastAPI 피드백 Polling 시작: 활성 세션 수={}", activeFastApiSessions.size());

        for (String fastApiSessionId : activeFastApiSessions) {
            try {
                // FastAPI에서 피드백 가져오기
                InferenceFeedbackDto feedback = fastApiSessionService.getFastApiFeedback(fastApiSessionId);
                
                if (feedback == null) {
                    continue; // 피드백 없음
                }

                // 중복 체크: 같은 타임스탬프면 건너뛰기
                Long lastTimestamp = lastFeedbackTimestamps.get(fastApiSessionId);
                if (lastTimestamp != null && 
                    feedback.getTimestamp() != null && 
                    feedback.getTimestamp().equals(lastTimestamp)) {
                    continue; // 이미 처리한 피드백
                }

                // Spring sessionId로 변환
                String springSessionId = inferenceSessionService
                    .getSpringSessionIdByFastApiSessionId(fastApiSessionId);
                
                if (springSessionId == null) {
                    log.warn("FastAPI sessionId에 해당하는 Spring sessionId를 찾을 수 없음: fastApiSessionId={}", 
                        fastApiSessionId);
                    continue;
                }

                // 피드백을 앱으로 전달
                boolean success = inferenceFeedbackService.sendFeedbackToApp(springSessionId, feedback);
                
                if (success && feedback.getTimestamp() != null) {
                    // 마지막 타임스탬프 저장
                    lastFeedbackTimestamps.put(fastApiSessionId, feedback.getTimestamp());
                    log.info("✅ FastAPI 피드백 Polling 성공: fastApiSessionId={}, springSessionId={}, timestamp={}", 
                        fastApiSessionId, springSessionId, feedback.getTimestamp());
                }
                
            } catch (Exception e) {
                log.error("FastAPI 피드백 Polling 오류: fastApiSessionId={}", fastApiSessionId, e);
            }
        }
    }

    /**
     * 세션 종료 시 타임스탬프 캐시 정리
     */
    public void clearSessionCache(String fastApiSessionId) {
        lastFeedbackTimestamps.remove(fastApiSessionId);
    }
}

