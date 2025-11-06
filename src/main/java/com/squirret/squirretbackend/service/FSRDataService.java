package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.dto.FSRDataDTO;
import com.squirret.squirretbackend.handler.FSRWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class FSRDataService {

    private final FSRWebSocketHandler fsrWebSocketHandler;

    // 왼쪽과 오른쪽 깔창 데이터 따로 저장
    private final AtomicReference<FSRDataDTO> latestLeftData = new AtomicReference<>();
    private final AtomicReference<FSRDataDTO> latestRightData = new AtomicReference<>();

    // ESP32 데이터 -> side 값 확인 뒤 일치하는 변수에 저장
    public void updateData(FSRDataDTO newData) {
        if ("left".equals(newData.getSide())) {
            this.latestLeftData.set(newData);
        } else if ("right".equals(newData.getSide())) {
            this.latestRightData.set(newData);
        }
        
        // 데이터 업데이트 시 웹소켓으로 실시간 브로드캐스트
        Map<String, FSRDataDTO> latestData = getLatestInsoleData();
        fsrWebSocketHandler.broadcastFSRData(latestData);
        log.debug("FSR 데이터 업데이트 및 웹소켓 브로드캐스트: {}", newData.getSide());
    }

    // 왼쪽과 오른쪽 데이터를 함께 반환하는 메소드 (앱 API용)
    public Map<String, FSRDataDTO> getLatestInsoleData() {
        Map<String, FSRDataDTO> insoleData = new HashMap<>();

        FSRDataDTO leftData = latestLeftData.get();
        FSRDataDTO rightData = latestRightData.get();

        // 데이터가 아직 없으면 빈 객체를 넣어줌
        insoleData.put("left", leftData != null ? leftData : new FSRDataDTO());
        insoleData.put("right", rightData != null ? rightData : new FSRDataDTO());

        return insoleData;
    }

}
