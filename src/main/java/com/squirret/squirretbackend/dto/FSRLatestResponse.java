package com.squirret.squirretbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "최신 FSR 데이터 스냅샷")
public class FSRLatestResponse {
    
    @Schema(description = "왼발 FSR 데이터", nullable = true)
    private FSRDataDTO left;
    
    @Schema(description = "오른발 FSR 데이터", nullable = true)
    private FSRDataDTO right;
}

