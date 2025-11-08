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
            merged.add("최근에 수집된 데이터가 없어 피드백을 제공할 수 없습니다.");
        }

        return CombinedFeedbackResponse.builder()
                .ai(aiFeedback)
                .fsr(fsrFeedback)
                .overallMessages(merged)
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
                messages.add("상체 정렬이 안정적입니다. 현재 자세를 유지하세요.");
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
        return switch (region) {
            case "lumbar", "허리" -> "허리를 곧게 펴고 복부에 힘을 주세요.";
            case "knee", "무릎" -> "무릎이 안쪽으로 무너지고 있습니다. 정렬을 유지하세요.";
            case "ankle", "발목" -> "발목을 안정적으로 고정하고 흔들림을 줄이세요.";
            default -> "자세를 다시 정렬해주세요.";
        };
    }

    private List<String> mergeMessages(CombinedFeedbackResponse.AiFeedback ai, FsrFeedbackResponse fsr) {
        List<String> merged = new ArrayList<>();
        if (ai != null && !CollectionUtils.isEmpty(ai.getMessages())) {
            merged.addAll(ai.getMessages());
        }

        if (fsr != null) {
            if (fsr.getLeft() != null && fsr.getLeft().getFeedback() != null) {
                merged.add("[왼발] " + fsr.getLeft().getFeedback());
            }
            if (fsr.getRight() != null && fsr.getRight().getFeedback() != null) {
                merged.add("[오른발] " + fsr.getRight().getFeedback());
            }
        }

        return merged;
    }
}

