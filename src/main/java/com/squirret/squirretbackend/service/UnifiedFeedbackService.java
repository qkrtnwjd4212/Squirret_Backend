package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.CombinedFeedbackResponse;
import com.squirret.squirretbackend.dto.FsrFeedbackResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UnifiedFeedbackService {

    private final AiStateStore aiStateStore;
    private final PostureFeedbackService postureFeedbackService;

    public CombinedFeedbackResponse buildFeedback() {
        Map<String, String> aiSnapshot = aiStateStore.snapshot();
        CombinedFeedbackResponse.AiFeedback aiFeedback = buildAiFeedback(aiSnapshot);

        FsrFeedbackResponse fsrFeedback = postureFeedbackService.getOverallFeedback();

        List<String> merged = mergeMessages(aiFeedback, fsrFeedback);
        if (merged.isEmpty()) {
            merged.add(limitFeedbackLength("데이터 수집 중입니다", 25));
        }
        
        // 모든 메시지를 25자로 제한
        List<String> limitedMessages = merged.stream()
                .map(msg -> limitFeedbackLength(msg, 25))
                .toList();

        return CombinedFeedbackResponse.builder()
                .ai(aiFeedback)
                .fsr(fsrFeedback)
                .overallMessages(limitedMessages)
                .build();
    }

    private CombinedFeedbackResponse.AiFeedback buildAiFeedback(Map<String, String> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return CombinedFeedbackResponse.AiFeedback.empty();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        snapshot.forEach((k, v) -> {
            if (v != null) {
                normalized.put(k, v.toLowerCase());
            }
        });

        List<String> messages = new ArrayList<>();
        boolean hasBad = false;
        boolean hasGood = false;

        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String region = entry.getKey();
            String value = entry.getValue();

            if ("bad".equals(value)) {
                hasBad = true;
                messages.add(messageFor(region));
            } else if ("good".equals(value)) {
                hasGood = true;
            }
        }

        String status;
        if (hasBad) {
            status = "BAD";
        } else if (hasGood) {
            status = "GOOD";
            if (messages.isEmpty()) {
                messages.add(limitFeedbackLength("상체 정렬이 안정적입니다", 25));
            }
        } else {
            status = "NO_DATA";
        }

        return CombinedFeedbackResponse.AiFeedback.builder()
                .status(status)
                .raw(normalized)
                .messages(messages)
                .build();
    }

    private String messageFor(String region) {
        String message = switch (region) {
            case "lumbar", "허리" -> "허리를 곧게 펴세요";
            case "knee", "무릎" -> "무릎 정렬을 유지하세요";
            case "ankle", "발목" -> "발목을 고정하세요";
            default -> "자세를 정렬해주세요";
        };
        
        // 피드백을 25자 이내로 제한
        return limitFeedbackLength(message, 25);
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

    private List<String> mergeMessages(CombinedFeedbackResponse.AiFeedback ai, FsrFeedbackResponse fsr) {
        List<String> merged = new ArrayList<>();
        
        // AI 피드백 메시지 추가 (첫 번째 메시지만, 25자 제한)
        if (ai != null && !CollectionUtils.isEmpty(ai.getMessages())) {
            String aiMessage = ai.getMessages().get(0);
            merged.add(limitFeedbackLength(aiMessage, 25));
        }

        // FSR 피드백 메시지 추가 (25자 제한)
        if (fsr != null && fsr.getFeedback() != null && !fsr.getFeedback().isEmpty()) {
            merged.add(limitFeedbackLength(fsr.getFeedback(), 25));
        }

        return merged;
    }
}

