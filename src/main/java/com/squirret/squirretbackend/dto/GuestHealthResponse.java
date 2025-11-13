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
@Schema(description = "게스트 헬스 체크 응답")
public class GuestHealthResponse {
    
    @Schema(description = "상태", example = "ok")
    private String status;
    
    @Schema(description = "모드", example = "guest")
    private String mode;
}

