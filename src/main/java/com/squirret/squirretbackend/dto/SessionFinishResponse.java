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
@Schema(description = "세션 완료 응답")
public class SessionFinishResponse {
    
    @Schema(description = "상태", example = "completed")
    private String status;
    
    @Schema(description = "세션 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String sessionId;
}

