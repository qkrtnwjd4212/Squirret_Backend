package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.service.AiStateStore;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/ai")
public class AiInputController {

    private final AiStateStore store;

    public AiInputController(AiStateStore store) {
        this.store = store;
    }

    @PostMapping("/status")
    public ResponseEntity<?> postStatus(@RequestBody Map<String, String> body) {
        String lumbar = normalize(body.get("lumbar"));
        String knee = normalize(body.get("knee"));
        String ankle = normalize(body.get("ankle"));

        if (!isValid(lumbar) || !isValid(knee) || !isValid(ankle)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "InvalidStatus",
                    "message", "lumbar/knee/ankle must be one of: good, bad, null"
            ));
        }

        store.update(lumbar, knee, ankle);
        return ResponseEntity.ok(Map.of("ok", true));
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


