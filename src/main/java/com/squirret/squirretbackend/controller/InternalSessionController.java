package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.InferenceFeedbackDto;
import com.squirret.squirretbackend.service.InferenceFeedbackService;
import com.squirret.squirretbackend.service.InferenceSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InternalSessionController {

    private final InferenceSessionService inferenceSessionService;
    private final InferenceFeedbackService inferenceFeedbackService;

    /**
     * 게스트 세션 발급 (기존 STOMP용)
     */
    @PostMapping("/internal/session")
    public ResponseEntity<Map<String, String>> issueSession(@RequestBody(required = false) Map<String, Object> body) {
        String sessionId = UUID.randomUUID().toString();
        // 기존 STOMP용 토큰은 별도 처리 필요 시 추가
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "wsToken", "stomp-token-placeholder"
        ));
    }

    /**
     * FastAPI 세션 발급 (REST API 기반)
     * Spring에서 FastAPI 세션을 생성하고 매핑
     */
    @PostMapping("/session")
    public ResponseEntity<InferenceSessionService.CreateSessionResponse> createInferenceSession(
            @RequestBody(required = false) Map<String, String> body) {
        String userId = body != null ? body.getOrDefault("userId", "guest") : "guest";
        String side = body != null ? body.getOrDefault("side", "auto") : "auto";
        InferenceSessionService.CreateSessionResponse response = inferenceSessionService.createSession(userId, side);
        return ResponseEntity.ok(response);
    }

    /**
     * 토큰 갱신
     */
    @PostMapping("/session/{sessionId}/refresh")
    public ResponseEntity<InferenceSessionService.RefreshTokenResponse> refreshToken(
            @PathVariable String sessionId) {
        InferenceSessionService.RefreshTokenResponse response = inferenceSessionService.refreshToken(sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * 세션 완료
     */
    @PostMapping("/session/{sessionId}/finish")
    public ResponseEntity<Map<String, String>> finishSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        Integer framesIn = body != null && body.containsKey("framesIn") 
                ? ((Number) body.get("framesIn")).intValue() : null;
        Integer framesOut = body != null && body.containsKey("framesOut") 
                ? ((Number) body.get("framesOut")).intValue() : null;
        Long durationSeconds = body != null && body.containsKey("durationSeconds") 
                ? ((Number) body.get("durationSeconds")).longValue() : null;

        InferenceSessionService.SessionStats stats = new InferenceSessionService.SessionStats(
                framesIn, framesOut, durationSeconds);
        inferenceSessionService.finishSession(sessionId, stats);
        return ResponseEntity.ok(Map.of("status", "completed", "sessionId", sessionId));
    }

    /**
     * FastAPI에서 분석 결과/피드백을 받아서 앱으로 전달
     * 
     * FastAPI sessionId를 받아서 Spring sessionId로 변환하여 피드백 전달
     * 
     * @param fastApiSessionId FastAPI 세션 ID (경로 변수)
     * @param feedback 피드백 데이터 (FastAPI 분석 결과)
     * @return 전달 결과
     */
    @PostMapping("/internal/inference/{fastApiSessionId}/feedback")
    public ResponseEntity<Map<String, Object>> receiveFeedback(
            @PathVariable String fastApiSessionId,
            @RequestBody InferenceFeedbackDto feedback) {
        log.info("FastAPI에서 피드백 수신: fastApiSessionId={}, type={}", fastApiSessionId, feedback.getType());
        
        // FastAPI sessionId로 Spring sessionId 조회
        String springSessionId = inferenceSessionService.getSpringSessionIdByFastApiSessionId(fastApiSessionId);
        if (springSessionId == null) {
            log.warn("FastAPI sessionId에 해당하는 Spring sessionId를 찾을 수 없음: fastApiSessionId={}", fastApiSessionId);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "fastApiSessionId", fastApiSessionId,
                    "message", "세션을 찾을 수 없습니다."
            ));
        }
        
        boolean success = inferenceFeedbackService.sendFeedbackToApp(springSessionId, feedback);
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "springSessionId", springSessionId,
                    "fastApiSessionId", fastApiSessionId,
                    "message", "피드백이 앱으로 전달되었습니다."
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "springSessionId", springSessionId,
                    "fastApiSessionId", fastApiSessionId,
                    "message", "피드백 전달에 실패했습니다."
            ));
        }
    }
}


