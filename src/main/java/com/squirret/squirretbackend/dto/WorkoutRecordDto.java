package com.squirret.squirretbackend.dto;

import java.time.Instant;
import java.util.UUID;

public class WorkoutRecordDto {
    private UUID id;
    // @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    // Swift가 포맷된 문자열을 보내고 명시적인 파싱이 필요한 경우 위에 있는 @JsonFormat을 사용하세요.
    // 그렇지 않으면 Instant는 기본적으로 ISO 8601 문자열(예: "2023-01-15T10:30:00Z")을 처리합니다.
    private Instant recordCreatedAt; // Swift의 Date에 적합한 시간의 한 지점을 나타냅니다.
    private int allCount;           // 스쿼트 수
    private double totalCalories;   // 칼로리 소모량
    private DifficultyType difficultyType; // 체감 난이도
    private int durationMinutes;    // 운동한 시간

    // Spring/Jackson 역직렬화를 위해 기본 생성자가 필요합니다.
    public WorkoutRecordDto() {}

    // 모든 필드를 포함하는 생성자 (선택 사항이지만 테스트/생성에 유용)
    public WorkoutRecordDto(UUID id, Instant recordCreatedAt, int allCount, double totalCalories, DifficultyType difficultyType, int durationMinutes) {
        this.id = id;
        this.recordCreatedAt = recordCreatedAt;
        this.allCount = allCount;
        this.totalCalories = totalCalories;
        this.difficultyType = difficultyType;
        this.durationMinutes = durationMinutes;
    }

    // Getters와 Setters (Spring/Jackson이 필드를 채우는 데 필요합니다)
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getRecordCreatedAt() {
        return recordCreatedAt;
    }

    public void setRecordCreatedAt(Instant recordCreatedAt) {
        this.recordCreatedAt = recordCreatedAt;
    }

    public int getAllCount() {
        return allCount;
    }

    public void setAllCount(int allCount) {
        this.allCount = allCount;
    }

    public double getTotalCalories() {
        return totalCalories;
    }

    public void setTotalCalories(double totalCalories) {
        this.totalCalories = totalCalories;
    }

    public DifficultyType getDifficultyType() {
        return difficultyType;
    }

    public void setDifficultyType(DifficultyType difficultyType) {
        this.difficultyType = difficultyType;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    @Override
    public String toString() {
        return "WorkoutRecordDto{" +
                "id=" + id +
                ", recordCreatedAt=" + recordCreatedAt +
                ", allCount=" + allCount +
                ", totalCalories=" + totalCalories +
                ", difficultyType=" + difficultyType +
                ", durationMinutes=" + durationMinutes +
                '}';
    }
}
