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
@Schema(description = "게스트 세션 생성 응답")
public class GuestSessionResponse {
    
    @Schema(description = "게스트 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID guestId;
    
    @Schema(description = "응답 메시지", example = "게스트 세션이 생성되었습니다.")
    private String message;
}

