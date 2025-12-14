package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.AIStatusRequest;
import com.squirret.squirretbackend.dto.AIStatusResponse;
import com.squirret.squirretbackend.dto.ErrorResponse;
import com.squirret.squirretbackend.service.AiStateStore;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/internal/ai")
public class AiInputController {

    private final AiStateStore store;

    public AiInputController(AiStateStore store) {
        this.store = store;
    }

    @PostMapping("/status")
    public ResponseEntity<?> postStatus(@RequestBody AIStatusRequest request) {
        String lumbar = normalize(request.getLumbar());
        String knee = normalize(request.getKnee());
        String ankle = normalize(request.getAnkle());

        if (!isValid(lumbar) || !isValid(knee) || !isValid(ankle)) {
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(400)
                    .error("Bad Request")
                    .code("INVALID_STATUS")
                    .message("lumbar/knee/ankle must be one of: good, bad, null")
                    .path("/internal/ai/status")
                    .build();
            return ResponseEntity.badRequest().body(error);
        }

        // 내부 테스트/디버깅용 엔드포인트이므로, 고정 userId를 사용해 전역 상태처럼 취급
        // 실제 서비스에서는 세션/게스트 ID 기반으로 분리된 상태를 사용하는 것을 권장
        store.update("internal-ai", lumbar, knee, ankle);
        AIStatusResponse response = AIStatusResponse.builder()
                .ok(true)
                .build();
        return ResponseEntity.ok(response);
    }

    private static String normalize(String v) {
        if (!StringUtils.hasText(v)) return null;
        return v.trim().toLowerCase();
    }

    private static boolean isValid(String v) {
        if (v == null) return true; // allow missing fields
        return v.equals("good") || v.equals("bad") || v.equals("null");
    }
}


