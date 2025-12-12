package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.InferenceFeedbackDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FastAPI에서 받은 피드백을 앱으로 전달하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InferenceFeedbackService {

    private final InferenceSessionService inferenceSessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AiStateStore aiStateStore;
    private final FeedbackHistoryService feedbackHistoryService;

    // 금지/치환 대상 문구 (부분 일치도 허용)
    private static final String[] BLOCKED_PHRASES = {
        "데이터 수집 중입니다",
        "데이터 수집중입니다",
        "데이터를 수집중입니다",
        "데이터 수집",
        "데이터수집" // 형태가 조금 달라도 잡히도록 키워드 추가
    };

    // FastAPI 피드백 치환에 사용할 응원/안내 문구
    private static final String[] ENCOURAGEMENT_MESSAGES = {
        "괜찮아요, 천천히 준비해볼까요?",
        "좋아요, 몸을 가볍게 풀어볼까요?",
        "화이팅! 준비가 되면 편하게 시작해 주세요.",
        "지금 페이스 좋아요, 편하게 이어가봐요.",
        "충분히 잘하고 있어요, 서두르지 않아도 돼요."
    };

    /**
     * FastAPI에서 넘어온 원본 피드백 텍스트를 앱으로 보내기 전에 정제
     * - "데이터 수집 중입니다" 계열 문구는 응원 메시지로 치환
     */
    private String sanitizeFeedbackText(String text) {
        if (text == null) {
            return null;
        }
        // 실제로 어떤 원문 코멘트가 들어오는지 확인하기 위한 로그
        log.info("🔎 sanitizeFeedbackText raw='{}'", text);

        String trimmed = text.trim();
        for (String blocked : BLOCKED_PHRASES) {
            // 전체 일치 또는 부분 포함 모두 차단
            if (trimmed.equals(blocked) || trimmed.contains(blocked)) {
                int n = ENCOURAGEMENT_MESSAGES.length;
                if (n == 0) {
                    return "";
                }
                int idx = ThreadLocalRandom.current().nextInt(n);
                return ENCOURAGEMENT_MESSAGES[idx];
            }
        }
        return text;
    }

    /**
     * FastAPI에서 받은 피드백을 앱으로 전달
     * 
     * @param sessionId 세션 ID
     * @param feedback 피드백 데이터
     * @return 전달 성공 여부
     */
    public boolean sendFeedbackToApp(String sessionId, InferenceFeedbackDto feedback) {
        // sessionId로 userId 조회
        String userId = inferenceSessionService.getUserIdBySessionId(sessionId);
        if (userId == null) {
            log.warn("세션을 찾을 수 없거나 만료됨: sessionId={}", sessionId);
            return false;
        }

        try {
            // 피드백 타입에 따라 다른 형식으로 전송
            if ("analysis".equals(feedback.getType())) {
                // 분석 결과를 DATA 형식으로 전송
                sendAnalysisResult(userId, feedback);
                
                // AI 상태 업데이트 (세션별 관리)
                Map<String, String> aiData = feedback.getAi();
                if (aiData == null && feedback.getChecks() != null) {
                    aiData = convertChecksToAi(feedback.getChecks());
                    log.info("📊 FastAPI checks를 AI 형식으로 변환: checks={} -> ai={}", 
                        feedback.getChecks(), aiData);
                }
                if (aiData != null && !aiData.isEmpty()) {
                    log.info("💾 AI 상태 저장 시도: userId={}, aiData={}", userId, aiData);
                    aiStateStore.update(
                        userId, // 세션별 상태 관리
                        aiData.getOrDefault("lumbar", null),
                        aiData.getOrDefault("knee", null),
                        aiData.getOrDefault("ankle", null)
                    );
                } else {
                    log.warn("⚠️ AI 데이터가 없어 상태 업데이트를 건너뜁니다: userId={}, feedback={}", 
                        userId, feedback);
                }
                
                // checks 기반으로 피드백 메시지 생성 및 전송 (25자 제한)
                String feedbackText = generateFeedbackFromChecks(feedback.getChecks());
                if (feedbackText != null && !feedbackText.isEmpty()) {
                    sendVoiceFeedback(userId, feedbackText, feedback.getTimestamp());
                }
            } else if ("feedback".equals(feedback.getType())) {
                // 피드백 메시지를 voice 형식으로 전송
                sendFeedbackMessage(userId, feedback);
            } else {
                // 기타 타입은 그대로 전송
                sendGenericMessage(userId, feedback);
            }

            log.info("피드백 전송 성공: sessionId={}, userId={}, type={}", 
                sessionId, userId, feedback.getType());
            return true;
        } catch (Exception e) {
            log.error("피드백 전송 실패: sessionId={}, userId={}", sessionId, userId, e);
            return false;
        }
    }

    /**
     * 분석 결과를 DATA 형식으로 전송
     * FastAPI 분석 결과를 STOMP 메시지 형식으로 변환
     */
    private void sendAnalysisResult(String userId, InferenceFeedbackDto feedback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "DATA");
        
        Map<String, Object> data = new HashMap<>();
        data.put("ts", feedback.getTimestamp() != null ? feedback.getTimestamp() : System.currentTimeMillis());
        
        // FastAPI checks를 ai 형식으로 변환 (기존 호환성)
        Map<String, String> aiData = feedback.getAi();
        if (aiData == null && feedback.getChecks() != null) {
            // FastAPI checks를 ai 형식으로 매핑
            aiData = convertChecksToAi(feedback.getChecks());
        }
        
        if (aiData != null && !aiData.isEmpty()) {
            data.put("ai", aiData);
        }
        
        // FastAPI 분석 결과 추가
        if (feedback.getState() != null) {
            data.put("state", feedback.getState());
        }
        
        if (feedback.getSide() != null) {
            data.put("side", feedback.getSide());
        }
        
        if (feedback.getSquatCount() != null) {
            data.put("squatCount", feedback.getSquatCount());
        }
        
        if (feedback.getChecks() != null) {
            data.put("checks", feedback.getChecks());
        }
        
        if (feedback.getScore() != null) {
            data.put("score", feedback.getScore());
        }
        
        if (feedback.getFrameNumber() != null) {
            data.put("frameNumber", feedback.getFrameNumber());
        }
        
        payload.put("payload", data);
        
        messagingTemplate.convertAndSendToUser(userId, "/queue/session", payload);
    }
    
    /**
     * FastAPI checks를 기존 ai 형식으로 변환
     * checks: {"back": "good", "knee": "too forward"} 
     * -> ai: {"lumbar": "good", "knee": "bad", "ankle": "good"}
     */
    private Map<String, String> convertChecksToAi(Map<String, String> checks) {
        Map<String, String> ai = new HashMap<>();
        
        // back -> lumbar 매핑
        if (checks.containsKey("back")) {
            String backValue = checks.get("back");
            // "good" -> "good", "too forward" -> "bad" 등
            ai.put("lumbar", normalizeAiValue(backValue));
        }
        
        // knee 그대로 사용
        if (checks.containsKey("knee")) {
            String kneeValue = checks.get("knee");
            ai.put("knee", normalizeAiValue(kneeValue));
        }
        
        // ankle이 checks에 있으면 사용, 없으면 null
        if (checks.containsKey("ankle")) {
            String ankleValue = checks.get("ankle");
            ai.put("ankle", normalizeAiValue(ankleValue));
        } else {
            ai.put("ankle", "null");
        }
        
        return ai;
    }
    
    /**
     * FastAPI checks 값을 ai 형식 값으로 정규화
     * "good" -> "good"
     * "too forward", "bad" 등 -> "bad"
     * 기타 -> "null"
     */
    private String normalizeAiValue(String value) {
        if (value == null) {
            return "null";
        }
        
        String lowerValue = value.toLowerCase();
        if ("good".equals(lowerValue) || "ok".equals(lowerValue)) {
            return "good";
        } else if (lowerValue.contains("bad") || lowerValue.contains("too") || 
                   lowerValue.contains("forward") || lowerValue.contains("backward")) {
            return "bad";
        } else {
            return "null";
        }
    }

    /**
     * 피드백 메시지를 voice 형식으로 전송
     * 피드백 텍스트는 25자 이내로 제한
     */
    private void sendFeedbackMessage(String userId, InferenceFeedbackDto feedback) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "voice");
        
        String raw = feedback.getFeedback() != null ? feedback.getFeedback() : "피드백이 없습니다.";
        String feedbackText = sanitizeFeedbackText(raw);
        String limited = limitFeedbackLength(feedbackText, 25);

        // 30초 쿨타임: 같은 문장은 30초 동안 다시 보내지 않음
        if (feedbackHistoryService.isUnderCooldown(userId, limited, 30_000L)) {
            log.debug("피드백 쿨타임으로 전송 스킵 (feedback): userId={}, text={}", userId, limited);
            return;
        }

        message.put("text", limited);
        
        if (feedback.getTimestamp() != null) {
            message.put("timestamp", feedback.getTimestamp());
        }
        
        messagingTemplate.convertAndSendToUser(userId, "/queue/session", message);
        // 마지막 피드백 & 전송 시각 저장
        feedbackHistoryService.markSent(userId, (String) message.get("text"));
    }

    /**
     * 일반 메시지 전송
     * 피드백 텍스트는 25자 이내로 제한
     */
    private void sendGenericMessage(String userId, InferenceFeedbackDto feedback) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", feedback.getType() != null ? feedback.getType() : "MESSAGE");
        
        if (feedback.getFeedback() != null) {
            String sanitized = sanitizeFeedbackText(feedback.getFeedback());
            String limited = limitFeedbackLength(sanitized, 25);

            if (feedbackHistoryService.isUnderCooldown(userId, limited, 30_000L)) {
                log.debug("피드백 쿨타임으로 전송 스킵 (generic): userId={}, text={}", userId, limited);
                return;
            }

            message.put("text", limited);
        }
        
        if (feedback.getAi() != null) {
            message.put("ai", feedback.getAi());
        }
        
        if (feedback.getScore() != null) {
            message.put("score", feedback.getScore());
        }
        
        if (feedback.getTimestamp() != null) {
            message.put("timestamp", feedback.getTimestamp());
        }
        
        messagingTemplate.convertAndSendToUser(userId, "/queue/session", message);
        if (message.containsKey("text")) {
            feedbackHistoryService.markSent(userId, (String) message.get("text"));
        }
    }
    
    /**
     * checks를 기반으로 피드백 메시지 생성 (25자 이내)
     */
    private String generateFeedbackFromChecks(Map<String, String> checks) {
        if (checks == null || checks.isEmpty()) {
            return null;
        }
        
        // 우선순위: knee > back > ankle
        if (checks.containsKey("knee")) {
            String kneeValue = checks.get("knee");
            if (kneeValue != null && !"good".equalsIgnoreCase(kneeValue)) {
                return limitFeedbackLength("무릎 정렬을 유지하세요", 25);
            }
        }
        
        if (checks.containsKey("back")) {
            String backValue = checks.get("back");
            if (backValue != null && !"good".equalsIgnoreCase(backValue)) {
                return limitFeedbackLength("허리를 곧게 펴세요", 25);
            }
        }
        
        if (checks.containsKey("ankle")) {
            String ankleValue = checks.get("ankle");
            if (ankleValue != null && !"good".equalsIgnoreCase(ankleValue)) {
                return limitFeedbackLength("발목을 고정하세요", 25);
            }
        }
        
        return null;
    }
    
    /**
     * voice 타입 피드백 전송 (25자 제한)
     */
    private void sendVoiceFeedback(String userId, String feedbackText, Long timestamp) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "voice");
        String limited = limitFeedbackLength(feedbackText, 25);

        if (feedbackHistoryService.isUnderCooldown(userId, limited, 30_000L)) {
            log.debug("피드백 쿨타임으로 전송 스킵 (voice): userId={}, text={}", userId, limited);
            return;
        }

        message.put("text", limited);
        
        if (timestamp != null) {
            message.put("timestamp", timestamp);
        } else {
            message.put("timestamp", System.currentTimeMillis());
        }
        
        messagingTemplate.convertAndSendToUser(userId, "/queue/session", message);
        feedbackHistoryService.markSent(userId, (String) message.get("text"));
    }
    
    /**
     * 피드백 텍스트를 지정된 길이로 제한
     * 한글, 영문 모두 문자 수로 계산 (바이트가 아닌 문자 수)
     * 
     * @param text 원본 텍스트
     * @param maxLength 최대 길이
     * @return 제한된 텍스트 (길이가 maxLength를 초과하면 말줄임표 없이 자름)
     */
    private String limitFeedbackLength(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        
        if (text.length() <= maxLength) {
            return text;
        }
        
        // 25자 초과 시 앞에서부터 자름 (말줄임표 없이)
        return text.substring(0, maxLength);
    }
}

