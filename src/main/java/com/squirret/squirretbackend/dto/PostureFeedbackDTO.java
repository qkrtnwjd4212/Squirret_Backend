package com.squirret.squirretbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostureFeedbackDTO {
    private String side;                    // "left" 또는 "right"
    private List<String> feedbacks;         // 피드백 메시지 리스트
    private String overallFeedback;         // 종합 피드백
    private PostureAnalysis analysis;       // 분석 데이터

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostureAnalysis {
        private float frontPressure;        // 앞쪽 압력 비율 (4, 6번)
        private float backPressure;         // 뒤쪽 압력 비율 (1, 5번)
        private float outerPressure;        // 바깥쪽 압력 비율 (1, 2, 3, 4번)
        private float innerPressure;        // 안쪽 압력 비율 (5, 6번)
        private float heelPressure;         // 발뒤꿈치 압력 비율 (1, 5번)
        private float forefootPressure;     // 앞발 압력 비율 (4, 6번)
    }
}

