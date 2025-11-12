package com.squirret.squirretbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * FastAPI Squat AI Service와의 통신을 담당하는 서비스
 * FastAPI는 REST API 기반으로 작동
 */
@Slf4j
@Service
public class FastApiSessionService {

    @Value("${fastapi.base-url:https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io}")
    private String fastApiBaseUrl;

    private final RestTemplate restTemplate;
    
    public String getFastApiBaseUrl() {
        return fastApiBaseUrl;
    }

    public FastApiSessionService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * FastAPI에 세션 생성 요청
     * 
     * @param side 분석 방향 ("auto", "left", "right")
     * @return FastAPI 세션 ID
     */
    public String createFastApiSession(String side) {
        String url = UriComponentsBuilder.fromHttpUrl(fastApiBaseUrl + "/api/session")
                .queryParam("side", side != null ? side : "auto")
                .toUriString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // FastAPI는 세션 ID를 문자열로 반환: "session_7f83a1f3"
                String sessionId = response.getBody().replace("\"", "").trim();
                log.info("FastAPI 세션 생성 성공: sessionId={}, side={}", sessionId, side);
                return sessionId;
            } else {
                log.error("FastAPI 세션 생성 실패: status={}", response.getStatusCode());
                throw new RuntimeException("FastAPI 세션 생성 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("FastAPI 세션 생성 오류: side={}", side, e);
            throw new RuntimeException("FastAPI 세션 생성 오류", e);
        }
    }

    /**
     * FastAPI 세션 상태 조회
     * 
     * @param fastApiSessionId FastAPI 세션 ID
     * @return 세션 상태 (문자열 또는 JSON)
     */
    public String getFastApiSessionStatus(String fastApiSessionId) {
        String url = fastApiBaseUrl + "/api/session/" + fastApiSessionId;

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                log.error("FastAPI 세션 상태 조회 실패: sessionId={}, status={}", 
                    fastApiSessionId, response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("FastAPI 세션 상태 조회 오류: sessionId={}", fastApiSessionId, e);
            return null;
        }
    }

    /**
     * FastAPI 세션 삭제
     * 
     * @param fastApiSessionId FastAPI 세션 ID
     * @return 삭제 성공 여부
     */
    public boolean deleteFastApiSession(String fastApiSessionId) {
        String url = fastApiBaseUrl + "/api/session/" + fastApiSessionId;

        try {
            restTemplate.delete(url);
            log.info("FastAPI 세션 삭제 성공: sessionId={}", fastApiSessionId);
            return true;
        } catch (Exception e) {
            log.error("FastAPI 세션 삭제 오류: sessionId={}", fastApiSessionId, e);
            return false;
        }
    }
}

