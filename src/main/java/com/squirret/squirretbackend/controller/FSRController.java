package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.service.FSRDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    // GET 요청 시 데이터 전송하는 엔드포인트 (좌/우 데이터 모두 포함)
    @GetMapping("/fsr_data/latest")
    public ResponseEntity<Map<String, FSRDataDTO>> getLatestFsrData() {
        return ResponseEntity.ok(fsrDataService.getLatestInsoleData());
    }

}
