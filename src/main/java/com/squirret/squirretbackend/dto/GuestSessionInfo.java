package com.squirret.squirretbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "게스트 세션 정보")
public class GuestSessionInfo {
    
    @Schema(description = "게스트 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID guestId;
    
    @Schema(description = "유효성 여부", example = "true")
    private Boolean valid;
    
    @Schema(description = "응답 메시지", example = "유효한 게스트 세션입니다.")
    private String message;
}

