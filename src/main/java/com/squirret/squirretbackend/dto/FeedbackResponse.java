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
@Schema(description = "피드백 전달 응답")
public class FeedbackResponse {
    
    @Schema(description = "상태", example = "success")
    private String status;
    
    @Schema(description = "Spring 세션 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String springSessionId;
    
    @Schema(description = "FastAPI 세션 ID", example = "session_7f83a1f3")
    private String fastApiSessionId;
    
    @Schema(description = "메시지", example = "피드백이 앱으로 전달되었습니다.")
    private String message;
}

