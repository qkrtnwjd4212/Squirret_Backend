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
@Schema(description = "세션 발급 응답")
public class SessionIssueResponse {
    
    @Schema(description = "세션 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private String sessionId;
    
    @Schema(description = "WebSocket 토큰 (현재는 placeholder)", example = "stomp-token-placeholder")
    private String wsToken;
}

