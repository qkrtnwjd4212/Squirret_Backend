package com.squirret.squirretbackend.service;

import config.WsSessionTracker;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FeedbackPushService {

    private final SimpMessagingTemplate messaging;
    private final WsSessionTracker tracker;
    private final AiStateStore aiStateStore;
    private final FeedbackHistoryService feedbackHistoryService;
    // 응원 문구 목록 (AI 데이터 없을 때)
    private final String[] encouragementMessages = {
        "화이팅! 조금만 더 힘내봐요!",
        "잘하고 있어요, 지금 페이스 좋아요.",
        "괜찮아요, 천천히 자세를 잡아볼까요?",
        "호흡을 가다듬고 한 번 더 도전해봐요.",
        "조금씩 좋아지고 있어요, 그대로 유지해봐요.",
        "좋아요! 몸의 긴장을 천천히 풀어봐요.",
        "아주 잘하고 있어요, 자신감을 가져봐요.",
        "지금처럼만 유지하면 분명 더 좋아질 거예요.",
        "몸의 감각을 느끼면서 천천히 움직여볼까요?",
        "충분히 잘하고 있어요, 서두르지 않아도 돼요."
    };
    // 긍정 피드백 문구 목록 (모든 부위가 good일 때)
    private final String[] positiveFeedbackMessages = {
        "좋은 자세입니다",
        "완벽한 자세예요",
        "훌륭해요, 그대로 유지하세요",
        "아주 잘하고 있어요",
        "자세가 안정적입니다",
        "완벽하게 하고 있어요",
        "지금 자세가 좋아요",
        "잘하고 있어요, 계속 유지하세요"
    };

    public FeedbackPushService(SimpMessagingTemplate messaging,
                               WsSessionTracker tracker,
                               AiStateStore aiStateStore,
                               FeedbackHistoryService feedbackHistoryService) {
        this.messaging = messaging;
        this.tracker = tracker;
        this.aiStateStore = aiStateStore;
        this.feedbackHistoryService = feedbackHistoryService;
    }

    // 1초마다 데이터 푸시
    @Scheduled(fixedRate = 1000)
    public void pushDataEverySecond() {
        long ts = System.currentTimeMillis();
        for (String user : tracker.getActiveUsers()) {
            // 각 사용자별 AI 상태 조회 (세션별 관리)
            Map<String, Object> ai = new HashMap<>(aiStateStore.snapshot(user));
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "DATA");
            Map<String, Object> data = new HashMap<>();
            data.put("value", Math.random());
            data.put("ts", ts);
            if (!ai.isEmpty()) {
                data.put("ai", ai);
            }
            payload.put("payload", data);
            messaging.convertAndSendToUser(user, "/queue/session", payload);
        }
    }

    // 10초마다 피드백 푸시 (요청 포맷: {type:"voice", text:"..."})
    @Scheduled(fixedRate = 10000, initialDelay = 5000)
    public void pushFeedbackEvery10Seconds() {
        for (String user : tracker.getActiveUsers()) {
            // 각 사용자별 AI 상태 조회 (세션별 관리)
            Map<String, String> ai = aiStateStore.snapshot(user);
            String text = resolveFeedback(ai, user);
            
            // 피드백이 있을 때만 전송
            if (text != null && !text.isEmpty()) {
                // 30초 쿨타임: 같은 문장은 30초 동안 다시 보내지 않음
                String limited = limitFeedbackLength(text, 25);
                if (feedbackHistoryService.isUnderCooldown(user, limited, 30_000L)) {
                    continue;
                }

                Map<String, Object> feedback = new HashMap<>();
                feedback.put("type", "voice");
                feedback.put("text", limited);
                messaging.convertAndSendToUser(user, "/queue/session", feedback);
                // 사용자별 마지막 피드백 & 전송 시각 저장 (다른 소스와도 공유)
                feedbackHistoryService.markSent(user, limited);
            }
        }
    }

    private String resolveFeedback(Map<String, String> ai, String user) {
        String lastFeedback = feedbackHistoryService.getLastFeedback(user);

        // 1) AI 상태가 없으면 (AI 동작 정보 없음) -> 응원 문구만 사용
        if (ai == null || ai.isEmpty()) {
            String encouragement = pickEncouragementMessage(lastFeedback);
            if (encouragement == null) {
                // 사용할 수 있는 새로운 문구가 없으면 이번 턴은 말하지 않음
                return null;
            }
            return limitFeedbackLength(encouragement, 25);
        }

        // 2) AI 동작 정보가 있는 경우: AI 피드백을 최우선으로 사용
        //    - 여러 부위가 bad일 수 있으므로, 후보 리스트를 만들고
        //    - 직전 문장과 다른 문장을 우선 선택
        String[] candidates = buildAiFeedbackCandidates(ai, user);
        String selected = null;

        // 직전 문장과 다른 후보를 먼저 찾는다
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            if (!candidate.equals(lastFeedback)) {
                selected = candidate;
                break;
            }
        }

        // 모든 후보가 직전 문장과 같다면 -> 같은 문장은 연속 금지이므로 이번 턴은 말하지 않음
        if (selected == null) {
            return null;
        }
        
        // 피드백을 25자 이내로 제한
        return limitFeedbackLength(selected, 25);
    }

    /**
     * AI 상태를 기반으로 피드백 후보 배열 생성
     * 우선순위: lumbar > knee > ankle
     * - 여러 부위가 bad일 경우 각 부위별 문장을 모두 후보로 포함
     * - 모든 부위가 good이면 긍정 피드백 후보를 여러 개 포함 (직전과 다른 것 선택)
     */
    private String[] buildAiFeedbackCandidates(Map<String, String> ai, String user) {
        // 최대 4개 후보 (lumbar, knee, ankle, good posture)
        String[] candidates = new String[4];
        int idx = 0;

        if ("bad".equals(ai.get("lumbar"))) {
            candidates[idx++] = "허리를 곧게 펴세요";
        }
        if ("bad".equals(ai.get("knee"))) {
            candidates[idx++] = "무릎 정렬을 유지하세요";
        }
        if ("bad".equals(ai.get("ankle"))) {
            // FSR 피드백과 문구를 통일하여 중복 판단이 정확히 동작하도록 함
            candidates[idx++] = "뒤꿈치에 체중을 실으세요";
        }

        // 모두 good인 경우: 긍정 피드백 후보를 여러 개 추가 (직전과 다른 것 선택)
        if (idx == 0) {
            // 긍정 피드백 문구들을 모두 후보로 추가 (resolveFeedback에서 직전과 다른 것 선택)
            for (String positiveMsg : positiveFeedbackMessages) {
                if (idx >= candidates.length) break; // 배열 크기 제한
                candidates[idx++] = positiveMsg;
            }
        }

        // 배열 길이보다 적게 채웠을 수 있으므로, 남는 칸은 null 유지
        return candidates;
    }

    /**
     * 응원 문구 중에서 직전 문장과 다른 문장을 랜덤으로 선택
     * - ThreadLocalRandom을 사용해 무작위 선택
     * - 모든 문장이 직전 문장과 같으면 null 반환
     */
    private String pickEncouragementMessage(String lastFeedback) {
        if (encouragementMessages.length == 0) {
            return null;
        }

        // 최대 N번까지 랜덤 시도하면서 직전 문장과 다른 문장을 찾는다
        int n = encouragementMessages.length;
        for (int i = 0; i < n; i++) {
            int idx = ThreadLocalRandom.current().nextInt(n);
            String candidate = encouragementMessages[idx];
            if (!candidate.equals(lastFeedback)) {
                return candidate;
            }
        }

        // 모든 시도에서 직전 문장과 동일했다면 사용 가능한 응원 문구 없음
        return null;
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


