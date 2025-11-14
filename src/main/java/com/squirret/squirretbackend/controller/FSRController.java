package com.squirret.squirretbackend.controller;

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

    @PostMapping("/fsr_data")
    public ResponseEntity<String> receiveFsrData(@RequestBody FSRDataDTO data) {
        // 센서 보정: 고장난 센서를 반대쪽 센서 값으로 대체
        applySensorCorrection(data);
        
        // 전압 값을 비율로 변환
        convertVoltageToRatio(data);
        
        fsrDataService.updateData(data);

        System.out.println("Updated FSR Data: " + data); // 로그 출력
        return ResponseEntity.ok("Data received successfully!");
    }

    /**
     * 고장난 센서를 반대쪽 센서 값으로 보정하는 메서드
     * - 왼쪽 깔창의 4번 센서 고장 → 오른쪽 깔창의 4번 센서 전압 사용
     * - 오른쪽 깔창의 6번 센서 고장 → 왼쪽 깔창의 6번 센서 전압 사용
     */
    private void applySensorCorrection(FSRDataDTO data) {
        if (data == null || data.getSide() == null) {
            return;
        }

        // 전압 값이 없으면 보정 불가
        if (data.getVoltage1() == null && data.getVoltage2() == null && 
            data.getVoltage3() == null && data.getVoltage4() == null && 
            data.getVoltage5() == null && data.getVoltage6() == null) {
            return;
        }

        // 반대쪽 최신 데이터 가져오기
        Map<String, FSRDataDTO> latestData = fsrDataService.getLatestInsoleData(false);
        FSRDataDTO oppositeData = null;

        if ("left".equalsIgnoreCase(data.getSide())) {
            oppositeData = latestData.get("right");
            // 왼쪽 4번 센서 고장 → 오른쪽 4번 센서 전압으로 보정
            if (oppositeData != null && oppositeData.getVoltage4() != null) {
                data.setVoltage4(oppositeData.getVoltage4());
                log.debug("왼쪽 4번 센서 보정: 오른쪽 4번 전압 {} 사용", oppositeData.getVoltage4());
            }
        } else if ("right".equalsIgnoreCase(data.getSide())) {
            oppositeData = latestData.get("left");
            // 오른쪽 6번 센서 고장 → 왼쪽 6번 센서 전압으로 보정
            if (oppositeData != null && oppositeData.getVoltage6() != null) {
                data.setVoltage6(oppositeData.getVoltage6());
                log.debug("오른쪽 6번 센서 보정: 왼쪽 6번 전압 {} 사용", oppositeData.getVoltage6());
            }
        }
    }

    /**
     * 전압 값을 비율로 변환하는 메서드
     * 아두이노에서 전압(voltage1~6)을 전송하면, 이를 비율(ratio1~6)로 변환합니다.
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
            return;
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

        // 비율 계산 (총 전압이 0보다 크면 비율 계산, 아니면 0)
        if (totalVoltage > 0) {
            data.setRatio1((voltage1 / totalVoltage) * 100.0f);
            data.setRatio2((voltage2 / totalVoltage) * 100.0f);
            data.setRatio3((voltage3 / totalVoltage) * 100.0f);
            data.setRatio4((voltage4 / totalVoltage) * 100.0f);
            data.setRatio5((voltage5 / totalVoltage) * 100.0f);
            data.setRatio6((voltage6 / totalVoltage) * 100.0f);
        } else {
            data.setRatio1(0.0f);
            data.setRatio2(0.0f);
            data.setRatio3(0.0f);
            data.setRatio4(0.0f);
            data.setRatio5(0.0f);
            data.setRatio6(0.0f);
        }

        log.debug("전압을 비율로 변환: totalVoltage={}, ratios=[{},{},{},{},{},{}]",
                totalVoltage, data.getRatio1(), data.getRatio2(), data.getRatio3(),
                data.getRatio4(), data.getRatio5(), data.getRatio6());
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
