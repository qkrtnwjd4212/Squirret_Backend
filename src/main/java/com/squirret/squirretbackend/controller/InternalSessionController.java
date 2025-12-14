package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.FeedbackResponse;
import com.squirret.squirretbackend.dto.InferenceFeedbackDto;
import com.squirret.squirretbackend.dto.RegisterSessionRequest;
import com.squirret.squirretbackend.dto.SessionFinishRequest;
import com.squirret.squirretbackend.dto.SessionFinishResponse;
import com.squirret.squirretbackend.dto.SessionIssueResponse;
import com.squirret.squirretbackend.service.InferenceFeedbackService;
import com.squirret.squirretbackend.service.InferenceSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InternalSessionController {

    private final InferenceSessionService inferenceSessionService;
    private final InferenceFeedbackService inferenceFeedbackService;
    private final com.squirret.squirretbackend.service.FastApiWebSocketClient fastApiWebSocketClient;

    /**
     * 게스트 세션 발급 (기존 STOMP용)
     */
    @PostMapping("/internal/session")
    public ResponseEntity<SessionIssueResponse> issueSession(@RequestBody(required = false) Object body) {
        String sessionId = UUID.randomUUID().toString();
        // 기존 STOMP용 토큰은 별도 처리 필요 시 추가
        SessionIssueResponse response = SessionIssueResponse.builder()
                .sessionId(sessionId)
                .wsToken("stomp-token-placeholder")
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * 프론트에서 발급받은 FastAPI 세션을 백엔드에 등록
     * 
     * 프론트엔드가 FastAPI에서 세션을 발급받은 후, 그 세션 ID를 백엔드에 전달하여 저장합니다.
     * 
     * @param request 요청 본문
     *   - userId: 게스트 ID (선택사항, 기본값: "guest")
     *   - fastApiSessionId: 프론트에서 FastAPI로부터 발급받은 세션 ID (필수)
     * @return 등록된 세션 정보
     */
    @PostMapping("/session")
    public ResponseEntity<InferenceSessionService.CreateSessionResponse> registerFastApiSession(
            @RequestBody RegisterSessionRequest request) {
        if (request == null || request.getFastApiSessionId() == null || request.getFastApiSessionId().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        String userId = request.getUserId() != null ? request.getUserId() : "guest";
        String fastApiSessionId = request.getFastApiSessionId();
        
        InferenceSessionService.CreateSessionResponse response = 
            inferenceSessionService.registerFastApiSession(userId, fastApiSessionId);
        
        // WebSocket 연결 시도
        String springSessionId = response.sessionId();
        boolean connected = fastApiWebSocketClient.connect(springSessionId, fastApiSessionId);
        if (connected) {
            log.info("FastAPI WebSocket 연결 성공: springSessionId={}, fastApiSessionId={}", 
                springSessionId, fastApiSessionId);
        } else {
            log.warn("FastAPI WebSocket 연결 실패: springSessionId={}, fastApiSessionId={}", 
                springSessionId, fastApiSessionId);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 세션 완료
     */
    @PostMapping("/session/{sessionId}/finish")
    public ResponseEntity<SessionFinishResponse> finishSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) SessionFinishRequest request) {
        Integer framesIn = request != null ? request.getFramesIn() : null;
        Integer framesOut = request != null ? request.getFramesOut() : null;
        Long durationSeconds = request != null && request.getDurationSeconds() != null 
                ? request.getDurationSeconds().longValue() : null;

        InferenceSessionService.SessionStats stats = new InferenceSessionService.SessionStats(
                framesIn, framesOut, durationSeconds);
        inferenceSessionService.finishSession(sessionId, stats);
        
        // 세션 종료 시 FastAPI WebSocket 연결도 종료
        fastApiWebSocketClient.disconnect(sessionId);
        log.info("세션 종료 시 WebSocket 연결 종료: sessionId={}", sessionId);
        
        SessionFinishResponse response = SessionFinishResponse.builder()
                .status("completed")
                .sessionId(sessionId)
                .build();
        return ResponseEntity.ok(response);
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
    public ResponseEntity<?> receiveFeedback(
            @PathVariable String fastApiSessionId,
            @RequestBody InferenceFeedbackDto feedback) {
        log.info("📥 FastAPI에서 피드백 수신: fastApiSessionId={}, type={}, state={}, checks={}", 
            fastApiSessionId, feedback.getType(), feedback.getState(), feedback.getChecks());
        
        // FastAPI sessionId로 Spring sessionId 조회
        String springSessionId = inferenceSessionService.getSpringSessionIdByFastApiSessionId(fastApiSessionId);
        if (springSessionId == null) {
            log.warn("FastAPI sessionId에 해당하는 Spring sessionId를 찾을 수 없음: fastApiSessionId={}", fastApiSessionId);
            FeedbackResponse errorResponse = FeedbackResponse.builder()
                    .status("error")
                    .fastApiSessionId(fastApiSessionId)
                    .message("세션을 찾을 수 없습니다.")
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
        
        boolean success = inferenceFeedbackService.sendFeedbackToApp(springSessionId, feedback);
        
        if (success) {
            FeedbackResponse response = FeedbackResponse.builder()
                    .status("success")
                    .springSessionId(springSessionId)
                    .fastApiSessionId(fastApiSessionId)
                    .message("피드백이 앱으로 전달되었습니다.")
                    .build();
            return ResponseEntity.ok(response);
        } else {
            FeedbackResponse errorResponse = FeedbackResponse.builder()
                    .status("error")
                    .springSessionId(springSessionId)
                    .fastApiSessionId(fastApiSessionId)
                    .message("피드백 전달에 실패했습니다.")
                    .build();
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}


