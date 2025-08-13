package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class FSRDataService {

    // 최신의 FSR 데이터를 저장할 변수
    private final AtomicReference<FSRDataDTO> latestData = new AtomicReference<>();

    // ESP32로부터 새로운 데이터가 들어왔을 때 데이터 갱신
    public void updateData(FSRDataDTO newData) {
        this.latestData.set(newData);
    }

    // GET 요청 들어왔을 때 저장된 최신 데이터를 반환
    public FSRDataDTO getLatestData() {
        return latestData.get() != null ? latestData.get() : new FSRDataDTO();
    }

}
