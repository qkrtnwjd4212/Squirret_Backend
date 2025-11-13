package com.squirret.squirretbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombinedFeedbackResponse {
    private AiFeedback ai;
    private FsrFeedbackResponse fsr;
    private List<String> overallMessages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiFeedback {
        private String status; // GOOD, BAD, NO_DATA
        private AIRawDTO raw; // lumbar/knee/ankle
        private List<String> messages;

        public static AiFeedback empty() {
            return AiFeedback.builder()
                    .status("NO_DATA")
                    .raw(null)
                    .messages(Collections.emptyList())
                    .build();
        }
    }
}

