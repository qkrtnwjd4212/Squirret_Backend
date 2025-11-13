package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.ErrorResponse;
import com.squirret.squirretbackend.dto.GuestHealthResponse;
import com.squirret.squirretbackend.dto.GuestSessionInfo;
import com.squirret.squirretbackend.dto.GuestSessionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/guest")
public class GuestController {
    
    @GetMapping("/")
    @ResponseBody
    public String index() {
        return "Squirret Backend API Server - Guest Mode";
    }
    
    /**
     * 게스트 세션 생성
     * 클라이언트가 처음 접속할 때 호출하여 게스트 ID를 발급받습니다.
     */
    @PostMapping("/session")
    public ResponseEntity<GuestSessionResponse> createGuestSession() {
        UUID guestId = UUID.randomUUID();
        GuestSessionResponse response = GuestSessionResponse.builder()
                .guestId(guestId)
                .message("게스트 세션이 생성되었습니다.")
                .build();
        return ResponseEntity.ok(response);
    }
    
    /**
     * 게스트 정보 조회
     * 게스트 ID를 받아서 유효성을 확인합니다.
     */
    @GetMapping("/session/{guestId}")
    public ResponseEntity<?> getGuestSession(@PathVariable String guestId) {
        try {
            UUID uuid = UUID.fromString(guestId); // UUID 형식 검증
            GuestSessionInfo info = GuestSessionInfo.builder()
                    .guestId(uuid)
                    .valid(true)
                    .message("유효한 게스트 세션입니다.")
                    .build();
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException e) {
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(400)
                    .error("Bad Request")
                    .code("INVALID_GUEST_ID")
                    .message("유효하지 않은 게스트 ID 형식입니다.")
                    .path("/api/guest/session/" + guestId)
                    .build();
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 헬스 체크
     */
    @GetMapping("/health")
    public ResponseEntity<GuestHealthResponse> health() {
        GuestHealthResponse response = GuestHealthResponse.builder()
                .status("ok")
                .mode("guest")
                .build();
        return ResponseEntity.ok(response);
    }
}

