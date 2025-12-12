package com.squirret.squirretbackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * FastAPI WebSocket 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FastApiWebSocketResponse {
    
    /**
     * 현재 상태 ("START", "SIT", "RISING", "STAND")
     */
    private String state;
    
    /**
     * 분석 중인 측면 ("left", "right", null)
     */
    private String side;
    
    /**
     * 누적 카운트
     */
    @JsonProperty("squat_count")
    private Integer squatCount;
    
    /**
     * 자세 평가
     * - back: 허리 평가 ("Good" | "Bad" | null)
     * - knee: 무릎 평가 ("Good" | "Bad" | null)
     * - ankle: 발목 평가 ("Good" | "Bad" | null)
     */
    private Map<String, String> checks;
    
    /**
     * 평균 점수
     */
    @JsonProperty("avg_score")
    private Double avgScore;
    
    /**
     * 에러/경고 메시지
     * - "": 정상 상태
     * - "가시성 낮음": 관절 가시성 부족
     * - "시간 초과. 다시 시작.": 타임아웃
     * - "자세 인식 실패": 포즈를 찾을 수 없음
     */
    private String message;
    
    /**
     * 한 번의 완료된 스쿼트에 대한 점수 (0...100) - 완료 프레임에서만 제공
     */
    private Double score;
    
    /**
     * score에 대한 직관적인 등급 - 완료 프레임에서만 제공
     * Perfect | Good | Fair | Poor
     */
    private String grade;
    
    /**
     * 에러 응답 필드
     */
    private Integer status;
    private String code;
}
