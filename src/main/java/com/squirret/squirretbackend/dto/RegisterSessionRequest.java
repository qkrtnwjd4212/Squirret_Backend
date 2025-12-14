package com.squirret.squirretbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "FastAPI 세션 등록 요청")
public class RegisterSessionRequest {
    
    @Schema(description = "게스트 ID (선택사항, 기본값: \"guest\")", example = "550e8400-e29b-41d4-a716-446655440000")
    private String userId;
    
    @NotBlank(message = "FastAPI 세션 ID는 필수입니다.")
    @Schema(description = "FastAPI에서 발급받은 세션 ID (필수)", example = "session_7f83a1f3", required = true)
    private String fastApiSessionId;
}

