package com.squirret.squirretbackend.service;

import config.WsSessionTracker;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class FeedbackPushService {

    private final SimpMessagingTemplate messaging;
    private final WsSessionTracker tracker;
    private final AiStateStore aiStateStore;

    public FeedbackPushService(SimpMessagingTemplate messaging, WsSessionTracker tracker, AiStateStore aiStateStore) {
        this.messaging = messaging;
        this.tracker = tracker;
        this.aiStateStore = aiStateStore;
    }

    // 1초마다 데이터 푸시
    @Scheduled(fixedRate = 1000)
    public void pushDataEverySecond() {
        long ts = System.currentTimeMillis();
        Map<String, Object> ai = new HashMap<>(aiStateStore.snapshot());
        for (String user : tracker.getActiveUsers()) {
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
        Map<String, String> ai = aiStateStore.snapshot();
        String text = resolveFeedback(ai);
        for (String user : tracker.getActiveUsers()) {
            Map<String, Object> feedback = new HashMap<>();
            feedback.put("type", "voice");
            feedback.put("text", text);
            messaging.convertAndSendToUser(user, "/queue/session", feedback);
        }
    }

    private String resolveFeedback(Map<String, String> ai) {
        String feedback;
        if (ai == null || ai.isEmpty()) {
            feedback = "뒷꿈치에 체중을 실으세요";
        } else if ("bad".equals(ai.get("lumbar"))) {
            feedback = "허리를 곧게 펴세요";
        } else if ("bad".equals(ai.get("knee"))) {
            feedback = "무릎 정렬을 유지하세요";
        } else if ("bad".equals(ai.get("ankle"))) {
            feedback = "뒷꿈치에 체중을 실으세요";
        } else {
            feedback = "좋은 자세입니다";
        }
        
        // 피드백을 25자 이내로 제한
        return limitFeedbackLength(feedback, 25);
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


