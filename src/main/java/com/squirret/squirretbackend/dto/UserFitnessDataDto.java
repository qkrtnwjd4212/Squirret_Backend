package com.squirret.squirretbackend.dto;

import java.util.List;
import java.util.UUID;

public class UserFitnessDataDto {
    private UUID userID; // Swift의 UserID에 해당합니다.
    private int acornCount; // 도토리 개수
    private List<WorkoutRecordDto> workoutRecordList; // 운동 기록 리스트

    // 기본 생성자가 필요합니다.
    public UserFitnessDataDto() {}

    // 모든 필드를 포함하는 생성자
    public UserFitnessDataDto(UUID userID, int acornCount, List<WorkoutRecordDto> workoutRecordList) {
        this.userID = userID;
        this.acornCount = acornCount;
        this.workoutRecordList = workoutRecordList;
    }

    // Getters와 Setters
    public UUID getUserID() {
        return userID;
    }

    public void setUserID(UUID userID) {
        this.userID = userID;
    }

    public int getAcornCount() {
        return acornCount;
    }

    public void setAcornCount(int acornCount) {
        this.acornCount = acornCount;
    }

    public List<WorkoutRecordDto> getWorkoutRecordList() {
        return workoutRecordList;
    }

    public void setWorkoutRecordList(List<WorkoutRecordDto> workoutRecordList) {
        this.workoutRecordList = workoutRecordList;
    }

    @Override
    public String toString() {
        return "UserFitnessDataDto{" +
                "userID=" + userID +
                ", acornCount=" + acornCount +
                ", workoutRecordList=" + workoutRecordList +
                '}';
    }
}
