package com.squirret.squirretbackend.controller;

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
     * FastAPI 웹소켓용 세션 발급
     */
    @PostMapping("/session")
    public ResponseEntity<InferenceSessionService.CreateSessionResponse> createInferenceSession(
            @RequestBody(required = false) Map<String, String> body) {
        String userId = body != null ? body.getOrDefault("userId", "guest") : "guest";
        InferenceSessionService.CreateSessionResponse response = inferenceSessionService.createSession(userId);
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
}


