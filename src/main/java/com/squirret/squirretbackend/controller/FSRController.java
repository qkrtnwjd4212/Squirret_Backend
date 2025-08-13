package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.service.FSRDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FSRController {

    private final FSRDataService fsrDataService;

    @PostMapping("/fsr_data")
    public ResponseEntity<String> receiveFsrData(@RequestBody FSRDataDTO data) {
        fsrDataService.updateData(data);

        System.out.println("Updated FSR Data: " + data); // 로그 출력
        return ResponseEntity.ok("Data received successfully!");
    }

    // GET 요청 시 데이터 전송하는 엔드포인트
    @GetMapping("/fsr_data/latest")
    public ResponseEntity<FSRDataDTO> getLatestFsrData() {
        // 서비스에 저장된 가장 최신 데이터를 가져옵니다.
        FSRDataDTO latestData = fsrDataService.getLatestData();

        // 최신 데이터를 JSON 형태로 앱에 응답합니다.
        return ResponseEntity.ok(latestData);
    }

}
