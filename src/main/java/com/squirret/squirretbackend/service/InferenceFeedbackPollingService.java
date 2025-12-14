package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.InferenceFeedbackDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * FastAPI(Squat API)에서 생성한 checks/ai 피드백을
 * 주기적으로 폴링(polling)해서 앱으로 전달하는 서비스.
 *
 * - FastAPI는 프레임을 받아서 내부적으로 checks/ai/state 등을 생성
 * - 이 서비스는 FastApiSessionService를 통해 그 결과를 가져와
 *   InferenceFeedbackService에 위임하여 WebSocket으로 전송한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InferenceFeedbackPollingService {

    private final FastApiSessionService fastApiSessionService;
    private final InferenceSessionService inferenceSessionService;
    private final InferenceFeedbackService inferenceFeedbackService;

    /**
     * 활성화된 FastAPI 세션에 대해 주기적으로 피드백을 조회하여
     * 앱으로 전달한다.
     *
     * - interval: 1초
     * - FastAPI가 아직 피드백을 생성하지 않았으면 null을 반환할 수 있으므로 조용히 skip
     * 
     * 현재 비활성화: FastAPI에서 직접 피드백을 Push하는 방식 사용
     */
    // @Scheduled(fixedRate = 1000)
    public void pollFastApiFeedback() {
        Set<String> activeFastApiSessions = inferenceSessionService.getActiveFastApiSessions();
        if (activeFastApiSessions.isEmpty()) {
            return;
        }

        for (String fastApiSessionId : activeFastApiSessions) {
            try {
                InferenceFeedbackDto feedback = fastApiSessionService.getFastApiFeedback(fastApiSessionId);
                if (feedback == null) {
                    continue; // 아직 피드백 없음
                }

                // FastAPI 세션 ID -> Spring 세션 ID로 매핑
                String springSessionId = inferenceSessionService.getSpringSessionIdByFastApiSessionId(fastApiSessionId);
                if (springSessionId == null) {
                    log.debug("Spring 세션 ID를 찾을 수 없어 피드백 전송을 건너뜀: fastApiSessionId={}", fastApiSessionId);
                    continue;
                }

                boolean sent = inferenceFeedbackService.sendFeedbackToApp(springSessionId, feedback);
                if (!sent) {
                    log.debug("피드백 전송 실패 또는 세션 만료: springSessionId={}, fastApiSessionId={}",
                            springSessionId, fastApiSessionId);
                }
            } catch (Exception e) {
                log.debug("FastAPI 피드백 폴링 중 오류 발생: fastApiSessionId={}, error={}",
                        fastApiSessionId, e.getMessage());
            }
        }
    }
}


