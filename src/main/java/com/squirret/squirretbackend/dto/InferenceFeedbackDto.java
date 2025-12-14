package com.squirret.squirretbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * FastAPI에서 전송하는 분석 결과/피드백 DTO
 * 
 * FastAPI Squat AI Service 응답 형식:
 * - state: "SIT", "STAND" 등
 * - side: "left", "right"
 * - squat_count: 숫자
 * - checks: {"back": "good", "knee": "too forward"} 등
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InferenceFeedbackDto {
    
    /**
     * 분석 결과 타입
     * - "analysis": 프레임 분석 결과 (FastAPI REST 응답)
     * - "feedback": 피드백 메시지
     */
    private String type;
    
    /**
     * 프레임 번호 (analysis 타입일 때)
     */
    private Integer frameNumber;
    
    /**
     * FastAPI 분석 결과 - 자세 상태
     * "SIT", "STAND" 등
     */
    private String state;
    
    /**
     * FastAPI 분석 결과 - 분석 방향
     * "left", "right"
     */
    private String side;
    
    /**
     * FastAPI 분석 결과 - 스쿼트 카운트
     */
    private Integer squatCount;
    
    /**
     * FastAPI 분석 결과 - 자세 체크 결과
     * 예: {"back": "good", "knee": "too forward", "lumbar": "bad"}
     */
    private Map<String, String> checks;
    
    /**
     * AI 분석 결과 (기존 형식 호환용)
     * 예: {"lumbar": "good", "knee": "bad", "ankle": "good"}
     * FastAPI의 checks를 ai로 변환하여 사용 가능
     */
    private Map<String, String> ai;
    
    /**
     * 점수 (analysis 타입일 때)
     */
    private Integer score;
    
    /**
     * 피드백 텍스트 (feedback 타입일 때)
     */
    private String feedback;
    
    /**
     * 타임스탬프
     */
    private Long timestamp;
}

