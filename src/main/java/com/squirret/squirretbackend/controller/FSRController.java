package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.CombinedFeedbackResponse;
import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.dto.FsrFeedbackResponse;
import com.squirret.squirretbackend.service.FSRDataService;
import com.squirret.squirretbackend.service.PostureFeedbackService;
import com.squirret.squirretbackend.service.UnifiedFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FSRController {

    private final FSRDataService fsrDataService;
    private final PostureFeedbackService postureFeedbackService;
    private final UnifiedFeedbackService unifiedFeedbackService;

    @PostMapping("/fsr_data")
    public ResponseEntity<String> receiveFsrData(@RequestBody FSRDataDTO data) {
        fsrDataService.updateData(data);

        System.out.println("Updated FSR Data: " + data); // 로그 출력
        return ResponseEntity.ok("Data received successfully!");
    }

    // GET 요청 시 데이터 전송하는 엔드포인트 (좌/우 데이터 모두 포함)
    @GetMapping("/fsr_data/latest")
    public ResponseEntity<Map<String, FSRDataDTO>> getLatestFsrData() {
        return ResponseEntity.ok(fsrDataService.getLatestInsoleData());
    }

    // 종합 자세 피드백 (양발 데이터 기반)
    @GetMapping("/fsr_data/feedback")
    public ResponseEntity<FsrFeedbackResponse> getFeedback() {
        log.info("=== 종합 자세 피드백 요청 ===");
        FsrFeedbackResponse feedback = postureFeedbackService.getOverallFeedback();
        log.info("종합 피드백 생성 완료 - stage={}, status={}", 
                feedback.getStage(), feedback.getStatus());
        return ResponseEntity.ok(feedback);
    }

    @GetMapping("/fsr_data/feedback/combined")
    public ResponseEntity<CombinedFeedbackResponse> getCombinedFeedback() {
        CombinedFeedbackResponse response = unifiedFeedbackService.buildFeedback();
        return ResponseEntity.ok(response);
    }

}
