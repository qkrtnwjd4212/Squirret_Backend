package com.squirret.squirretbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "에러 응답")
public class ErrorResponse {
    
    @Schema(description = "타임스탬프", example = "2024-01-01T00:00:00")
    private LocalDateTime timestamp;
    
    @Schema(description = "HTTP 상태 코드", example = "400")
    private Integer status;
    
    @Schema(description = "에러 타입", example = "Bad Request")
    private String error;
    
    @Schema(description = "에러 코드", example = "INVALID_GUEST_ID")
    private String code;
    
    @Schema(description = "에러 메시지", example = "유효하지 않은 게스트 ID 형식입니다.")
    private String message;
    
    @Schema(description = "요청 경로", example = "/api/guest/session/invalid-id")
    private String path;
}

