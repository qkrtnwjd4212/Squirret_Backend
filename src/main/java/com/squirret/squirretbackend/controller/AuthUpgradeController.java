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
@RequestMapping("/auth")
public class AuthUpgradeController {

    private final JwtService jwtService;

    public AuthUpgradeController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/upgrade")
    public ResponseEntity<Map<String, String>> upgrade(@RequestBody Map<String, String> body) {
        String userIdStr = body.get("userId");
        String sessionId = body.get("sessionId");
        UUID userId = UUID.fromString(userIdStr);

        String pseudoEmail = (body.getOrDefault("email", null) != null)
                ? body.get("email")
                : (userId + "@user.squirret");

        String wsToken = jwtService.generateToken(userId, pseudoEmail);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "wsToken", wsToken
        ));
    }
}


