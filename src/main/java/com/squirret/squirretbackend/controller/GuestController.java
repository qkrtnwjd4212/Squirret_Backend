package com.squirret.squirretbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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
    public ResponseEntity<Map<String, Object>> createGuestSession() {
        String guestId = UUID.randomUUID().toString();
        return ResponseEntity.ok(Map.of(
            "guestId", guestId,
            "message", "게스트 세션이 생성되었습니다."
        ));
    }
    
    /**
     * 게스트 정보 조회
     * 게스트 ID를 받아서 유효성을 확인합니다.
     */
    @GetMapping("/session/{guestId}")
    public ResponseEntity<Map<String, Object>> getGuestSession(@PathVariable String guestId) {
        try {
            UUID.fromString(guestId); // UUID 형식 검증
            return ResponseEntity.ok(Map.of(
                "guestId", guestId,
                "valid", true,
                "message", "유효한 게스트 세션입니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "InvalidGuestId",
                "message", "유효하지 않은 게스트 ID 형식입니다."
            ));
        }
    }
    
    /**
     * 헬스 체크
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "mode", "guest"));
    }
}

