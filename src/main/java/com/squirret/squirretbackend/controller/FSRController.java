package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.dto.PostureFeedbackDTO;
import com.squirret.squirretbackend.service.FSRDataService;
import com.squirret.squirretbackend.service.PostureFeedbackService;
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
    public ResponseEntity<PostureFeedbackDTO> getFeedback() {
        log.info("=== 종합 자세 피드백 요청 ===");
        PostureFeedbackDTO feedback = postureFeedbackService.getOverallFeedback();
        log.info("종합 피드백 생성 완료 - 피드백 개수: {}", feedback.getFeedbacks().size());
        return ResponseEntity.ok(feedback);
    }

}
