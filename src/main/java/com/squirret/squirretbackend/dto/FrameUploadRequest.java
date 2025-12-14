package com.squirret.squirretbackend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 프론트에서 프레임 업로드 요청 DTO
 */
@Data
@NoArgsConstructor
public class FrameUploadRequest {
    /**
     * JPEG를 base64로 인코딩한 문자열
     */
    private String image_base64;
    
    /**
     * Spring 세션 ID (선택사항, 없으면 URL에서 추출)
     */
    private String sessionId;
}

