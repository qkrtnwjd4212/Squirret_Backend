package com.squirret.squirretbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "FSR 메트릭 데이터")
public class FSRMetricsDTO {
    @Schema(description = "전면 압력 비율", example = "45.5")
    private Float front;
    
    @Schema(description = "후면 압력 비율", example = "54.5")
    private Float rear;
    
    @Schema(description = "안쪽 압력 비율", example = "50.0")
    private Float inner;
    
    @Schema(description = "바깥쪽 압력 비율", example = "50.0")
    private Float outer;
    
    @Schema(description = "뒤꿈치 압력 비율", example = "55.0")
    private Float heel;
    
    @Schema(description = "안쪽-바깥쪽 차이", example = "5.0")
    private Float innerOuterDiff;
    
    @Schema(description = "좌우 차이", example = "10.0")
    private Float leftRightDiff;
}

