package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalSessionController {

    private final JwtService jwtService;

    public InternalSessionController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> issueSession(@RequestBody(required = false) Map<String, Object> body) {
        String providedUserId = body != null ? (String) body.getOrDefault("userId", null) : null;

        String sessionId = UUID.randomUUID().toString();
        UUID userId = providedUserId != null ? UUID.fromString(providedUserId) : UUID.randomUUID();
        String pseudoEmail = sessionId + "@ws.squirret"; // JWTService가 email(subject)을 요구하므로 임시 주체 사용

        String wsToken = jwtService.generateToken(userId, pseudoEmail);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "wsToken", wsToken
        ));
    }
}


