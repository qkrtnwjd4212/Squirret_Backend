package com.squirret.squirretbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 게스트 모드에서 세션 업그레이드 (JWT 없이 게스트 ID만 사용)
 */
@RestController
@RequestMapping("/auth")
public class AuthUpgradeController {

    @PostMapping("/upgrade")
    public ResponseEntity<Map<String, String>> upgrade(@RequestBody Map<String, String> body) {
        String userIdStr = body.get("userId");
        String sessionId = body.get("sessionId");
        
        if (userIdStr == null || sessionId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "userId와 sessionId가 필요합니다."
            ));
        }
        
        try {
            UUID.fromString(userIdStr); // UUID 형식 검증
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "유효하지 않은 userId 형식입니다."
            ));
        }

        // 게스트 모드에서는 JWT 없이 세션 ID만 반환
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "guestId", userIdStr,
                "mode", "guest"
        ));
    }
}


