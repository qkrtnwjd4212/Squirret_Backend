package com.squirret.squirretbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FsrFeedbackResponse {
    private String stage;    // DESCENT, ASCENT, UNKNOWN
    private String status;   // GOOD, BAD, NO_DATA
    private String feedback;
    private Map<String, Float> metrics; // front, rear, inner, outer, heel, leftRightDiff
}

