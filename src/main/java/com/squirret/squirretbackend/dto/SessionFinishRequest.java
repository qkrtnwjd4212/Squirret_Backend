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
@Schema(description = "세션 완료 요청")
public class SessionFinishRequest {
    
    @Schema(description = "입력 프레임 수", example = "100")
    private Integer framesIn;
    
    @Schema(description = "출력 프레임 수", example = "95")
    private Integer framesOut;
    
    @Schema(description = "세션 지속 시간 (초)", example = "120")
    private Integer durationSeconds;
}

