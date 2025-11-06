package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.AuthResponseDto;
import com.squirret.squirretbackend.entity.User;
import com.squirret.squirretbackend.service.JwtService;
import com.squirret.squirretbackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AuthController {
    
    private final JwtService jwtService;
    private final UserService userService;
    
    @GetMapping("/")
    @ResponseBody
    public String index() {
        return "Squirret Backend API Server";
    }

    @GetMapping("/login")
    @ResponseBody
    public String login() {
        return "Login page - OAuth login links removed";
    }

    @GetMapping("/me")
    @ResponseBody
    public Map<String, Object> me(@AuthenticationPrincipal OAuth2User user) {
        return user == null ? Map.of("authenticated", false) : user.getAttributes();
    }
    
    @GetMapping("/api/auth/me")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "인증되지 않은 사용자입니다."));
        }
        
        String email = principal.getAttribute("email");
        User user = userService.findByEmail(email).orElse(null);
        
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "사용자를 찾을 수 없습니다."));
        }
        
        return ResponseEntity.ok(Map.of(
            "id", user.getId(),
            "email", user.getEmail(),
            "name", user.getName(),
            "profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "",
            "provider", user.getProvider()
        ));
    }
    
    @PostMapping("/api/auth/refresh")
    @ResponseBody
    public ResponseEntity<AuthResponseDto> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body(new AuthResponseDto("리프레시 토큰이 필요합니다.", null, null));
        }
        
        try {
            String email = jwtService.extractUsername(refreshToken);
            UUID userId = jwtService.extractUserId(refreshToken);
            
            if (jwtService.isTokenValid(refreshToken, email)) {
                String newAccessToken = jwtService.generateToken(userId, email);
                String newRefreshToken = jwtService.generateRefreshToken(userId, email);
                
                return ResponseEntity.ok(new AuthResponseDto("토큰이 성공적으로 갱신되었습니다.", newAccessToken, newRefreshToken));
            } else {
                return ResponseEntity.status(401).body(new AuthResponseDto("유효하지 않은 리프레시 토큰입니다.", null, null));
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new AuthResponseDto("토큰 처리 중 오류가 발생했습니다.", null, null));
        }
    }
    
    @PostMapping("/api/auth/logout")
    @ResponseBody
    public ResponseEntity<Map<String, String>> logout() {
        // JWT는 stateless이므로 서버에서 별도 처리할 필요 없음
        // 클라이언트에서 토큰을 삭제하면 됨
        return ResponseEntity.ok(Map.of("message", "로그아웃되었습니다."));
    }
}
