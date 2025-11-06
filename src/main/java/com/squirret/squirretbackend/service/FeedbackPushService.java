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
        if (ai == null || ai.isEmpty()) return "뒷꿈치를 좀 더 누르세요";
        if ("bad".equals(ai.get("lumbar"))) return "허리를 곧게 펴세요";
        if ("bad".equals(ai.get("knee"))) return "무릎이 앞으로 나가지 않게 하세요";
        if ("bad".equals(ai.get("ankle"))) return "뒷꿈치를 좀 더 누르세요";
        return "좋아요! 지금 자세를 유지하세요";
    }
}


