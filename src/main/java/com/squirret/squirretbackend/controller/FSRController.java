package com.squirret.squirretbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squirret.squirretbackend.dto.CombinedFeedbackResponse;
import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.dto.FSRLatestResponse;
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
    private final ObjectMapper objectMapper;

    @PostMapping("/fsr_data")
    public ResponseEntity<String> receiveFsrData(@RequestBody String jsonString) {
        log.info("=== FSR 데이터 수신 시작 ===");
        log.info("수신된 원본 JSON 문자열: {}", jsonString);
        
        // JSON 파싱
        FSRDataDTO data;
        try {
            data = objectMapper.readValue(jsonString, FSRDataDTO.class);
            log.info("✅ JSON 파싱 성공!");
            log.info("파싱된 데이터 - side: {}", data.getSide());
            log.info("Voltage 값들 - v1: {}, v2: {}, v3: {}, v4: {}, v5: {}, v6: {}", 
                    data.getVoltage1(), data.getVoltage2(), data.getVoltage3(),
                    data.getVoltage4(), data.getVoltage5(), data.getVoltage6());
        } catch (Exception e) {
            log.error("❌ JSON 파싱 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("JSON parsing error: " + e.getMessage());
        }
        
        // 전압 값을 비율로 변환
        convertVoltageToRatio(data);
        
        log.info("변환 후 ratio - ratio1: {}, ratio2: {}, ratio3: {}, ratio4: {}, ratio5: {}, ratio6: {}", 
                data.getRatio1(), data.getRatio2(), data.getRatio3(), 
                data.getRatio4(), data.getRatio5(), data.getRatio6());
        
        fsrDataService.updateData(data);

        log.info("=== FSR 데이터 저장 완료 ===");
        return ResponseEntity.ok("Data received successfully!");
    }

    /**
     * 전압 값을 비율로 변환하는 메서드
     * 아두이노에서 전압(voltage1~6)을 전송하면, 이를 비율(ratio1~6)로 변환합니다.
     * voltage가 없거나 0이면 아두이노에서 보낸 ratio를 그대로 유지합니다.
     */
    private void convertVoltageToRatio(FSRDataDTO data) {
        Float v1 = data.getVoltage1();
        Float v2 = data.getVoltage2();
        Float v3 = data.getVoltage3();
        Float v4 = data.getVoltage4();
        Float v5 = data.getVoltage5();
        Float v6 = data.getVoltage6();

        // 전압 값이 모두 null이면 변환하지 않음 (이미 비율로 전송된 경우)
        if (v1 == null && v2 == null && v3 == null && v4 == null && v5 == null && v6 == null) {
            log.info("전압 값이 없으므로 ratio 변환 건너뜀 (아두이노에서 이미 ratio로 전송된 경우)");
            return;  // 아두이노에서 보낸 ratio를 그대로 유지
        }

        // null 값을 0으로 처리
        float voltage1 = (v1 != null) ? v1 : 0.0f;
        float voltage2 = (v2 != null) ? v2 : 0.0f;
        float voltage3 = (v3 != null) ? v3 : 0.0f;
        float voltage4 = (v4 != null) ? v4 : 0.0f;
        float voltage5 = (v5 != null) ? v5 : 0.0f;
        float voltage6 = (v6 != null) ? v6 : 0.0f;

        // 총 전압 계산
        float totalVoltage = voltage1 + voltage2 + voltage3 + voltage4 + voltage5 + voltage6;

        // 비율 계산 (총 전압이 0보다 크면 비율 계산)
        if (totalVoltage > 0) {
            data.setRatio1((voltage1 / totalVoltage) * 100.0f);
            data.setRatio2((voltage2 / totalVoltage) * 100.0f);
            data.setRatio3((voltage3 / totalVoltage) * 100.0f);
            data.setRatio4((voltage4 / totalVoltage) * 100.0f);
            data.setRatio5((voltage5 / totalVoltage) * 100.0f);
            data.setRatio6((voltage6 / totalVoltage) * 100.0f);
            log.info("전압을 비율로 변환: totalVoltage={}, ratios=[{},{},{},{},{},{}]",
                    totalVoltage, data.getRatio1(), data.getRatio2(), data.getRatio3(),
                    data.getRatio4(), data.getRatio5(), data.getRatio6());
        } else {
            // totalVoltage가 0이면 ratio를 0으로 덮어쓰지 않고 그대로 유지
            // (아두이노에서 이미 ratio를 보낸 경우를 위해)
            log.warn("총 전압이 0입니다. ratio는 변경하지 않습니다. (현재 ratio: [{},{},{},{},{},{}])",
                    data.getRatio1(), data.getRatio2(), data.getRatio3(),
                    data.getRatio4(), data.getRatio5(), data.getRatio6());
            // ratio를 0으로 덮어쓰지 않음!
        }
    }

    // GET 요청 시 데이터 전송하는 엔드포인트 (좌/우 데이터 모두 포함)
    @GetMapping("/fsr_data/latest")
    public ResponseEntity<FSRLatestResponse> getLatestFsrData() {
        return ResponseEntity.ok(fsrDataService.getLatestInsoleDataAsResponse());
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
