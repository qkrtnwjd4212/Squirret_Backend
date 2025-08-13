package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FSRController {

    @PostMapping("/fsr_data")
    public ResponseEntity<String> receiveFsrData(@RequestBody FSRDataDTO data) {
        // ESP32로부터 받은 데이터를 출력
        System.out.println("Received FSR Data: " + data.toString());

        // DB 저장하는 비즈니스 로직

        // ESP32에게 성공적으로 받았다는 응답 보냄
        return ResponseEntity.ok("Data received successfully!");
    }
}
