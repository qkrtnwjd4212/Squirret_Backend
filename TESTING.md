
# Squirret Backend API 명세

## 0) 공통

* **Base URL**: `http://54.86.161.187:8080`
* **WS Base**: `ws://54.86.161.187:8080`
* **Content-Type**: `application/json; charset=utf-8`
* **인증**: 
  - WebSocket(STOMP) 접속 시 `?token=<wsToken>` (게스트 모드)
  - REST API는 현재 공개 (게스트 모드)
* **시간**: Unix epoch sec (밀리초) 또는 ISO-8601
* **데이터베이스**: MySQL (54.86.161.187:3306/squirretDB)
* **오류 포맷(공통)**:

```json
{
  "timestamp": "2025-11-09T07:10:23Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "ratio1 must be between 0 and 100",
  "path": "/api/fsr_data"
}
```

---

## 1) 전체 흐름 한눈에

1. **게스트 세션 발급** → `sessionId`, `wsToken` 획득
2. **STOMP 연결**(`ws://54.86.161.187:8080/ws?token=...`) → **개인 큐**(`/user/queue/session`) 구독
3. **앱 → 서버 송신**(`/app/session.message`) 필요 시 사용
4. **FSR(깔창) 데이터**: REST(스냅샷/피드백) + WS(실시간) 병행

---

## 3) 세션/인증 API

### 3.1 게스트 세션 발급

* **POST** `/internal/session` (바디 없음 또는 빈 JSON `{}`)
* **응답 200**

```json
{ "sessionId": "e0e1c6af-...", "wsToken": "stomp-token-placeholder" }
```

**참고**: 현재 구현에서는 `wsToken`이 placeholder로 반환됩니다. 실제 STOMP 연결 시 JWT 토큰이 필요합니다.

---

## 4) WebSocket/STOMP

### 4.1 연결 정보

* **URL**: `ws://54.86.161.187:8080/ws?token=<wsToken>`
* **프로토콜**: 순수 WebSocket + STOMP 1.2
* **필수**: 모든 STOMP 프레임 끝에 널 문자 `\u0000`
* **구독**: `/user/queue/session`
* **송신**: `/app/session.message` (JSON)

### 4.2 메시지 규격(서버→앱 예시)

```json
{
  "type": "DATA",
  "payload": {
    "value": 0.123,
    "ts": 1730892345,
    "ai": { "lumbar": "good|bad|null", "knee": "good|bad|null", "ankle": "good|bad|null" }
  }
}
```

```json
{ "type": "voice", "text": "뒷꿈치를 좀 더 누르세요" }
```

---

## 5) FSR(깔창) 데이터

### 5.1 업로드(ESP32)

* **POST** `/api/fsr_data`
* **요청**

```json
{
  "side":"left",           // "left" | "right"
  "ratio1":12.3, "ratio2":8.4, "ratio3":5.1,
  "ratio4":20.0, "ratio5":32.5, "ratio6":21.7
}
```

* **응답 202**: `{ "status": "ACCEPTED" }`
* **검증**: `ratio1~6` ∈ [0,100]

### 5.2 최신 스냅샷(REST)

* **GET** `/api/fsr_data/latest`
* **응답 200**

```json
{
  "left":  { "side":"left","ratio1":15.2,"ratio2":7.5,"ratio3":3.1,"ratio4":25.4,"ratio5":30.0,"ratio6":18.8 },
  "right": null
}
```

### 5.3 실시간 스트리밍(WS 브로드캐스트)

* **URL**: `ws://54.86.161.187:8080/ws/fsr-data` (STOMP 아님, **순수 WebSocket JSON**)
* **동작**: 연결 즉시 최신 스냅샷 1회 → 새 데이터마다 push

### 5.4 10초 구간 피드백(REST)

* **GET** `/api/fsr_data/feedback`
* **응답 200(예시)**: 양발 데이터를 평균 내어 전반적인 균형 기반 피드백 제공

```json
{
  "stage": "DESCENT",
  "status": "BAD",
  "feedback": "체중이 앞쪽으로 쏠렸습니다. 뒤꿈치로 눌러주세요. 무릎이 안쪽으로 모이고 있습니다. 양발에 체중을 균등하게 분배하세요.",
  "metrics": {
    "front": 48.3,
    "rear": 52.1,
    "inner": 62.0,
    "outer": 55.5,
    "heel": 52.1,
    "innerOuterDiff": 6.5,
    "leftRightDiff": 8.2
  }
}
```

* **판정 요약**:

  * **DESCENT BAD**: 앞쪽>40%, 안/바깥쪽>60%, 뒤꿈치 부족, 좌우 불균형(>15%) 등
  * **ASCENT BAD**: 뒤꿈치 <40%, 안/바깥쪽>60%, 앞·뒤 불균형, 좌우 불균형(>15%) 등
  * **NO_DATA**: 직전 10초 수집값 없음
  * **metrics**: 양발 평균값 + 좌우 균형 차이(`leftRightDiff`) 포함

### 5.5 AI + FSR 통합 피드백(REST)

* **GET** `/api/fsr_data/feedback/combined`
* **응답 200(예시)**: AI 상태와 FSR 통합 피드백을 전반적인 메시지로 제공
* **특징**: 
  - AI만 있을 경우: AI 피드백만 제공
  - FSR만 있을 경우: FSR 피드백만 제공
  - 둘 다 있을 경우: AI + FSR 피드백 통합 제공

```json
{
  "ai": {
    "status": "BAD",
    "raw": { "lumbar": "good", "knee": "bad", "ankle": "null" },
    "messages": ["무릎이 안쪽으로 무너지고 있습니다. 정렬을 유지하세요."]
  },
  "fsr": {
    "stage": "DESCENT",
    "status": "BAD",
    "feedback": "체중이 앞쪽으로 쏠렸습니다. 뒤꿈치로 눌러주세요.",
    "metrics": { "front": 48.3, "rear": 52.1, "inner": 62.0, "outer": 55.5, "heel": 52.1, "innerOuterDiff": 6.5, "leftRightDiff": 8.2 }
  },
  "overallMessages": [
    "무릎이 안쪽으로 무너지고 있습니다. 정렬을 유지하세요.",
    "체중이 앞쪽으로 쏠렸습니다. 뒤꿈치로 눌러주세요."
  ]
}
```

---

## 7) cURL 퀵스타트

```bash
# 1) 게스트 세션 발급
curl -s -X POST -H "Content-Type: application/json" \
  http://54.86.161.187:8080/internal/session | jq

# 2) FSR 최신 스냅샷
curl -s http://54.86.161.187:8080/api/fsr_data/latest | jq

# 3) FSR 피드백 (양발 통합)
curl -s http://54.86.161.187:8080/api/fsr_data/feedback | jq

# 3-1) AI + FSR 통합 피드백
curl -s http://54.86.161.187:8080/api/fsr_data/feedback/combined | jq

# 4) AI 상태 입력(내부)
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"lumbar":"good","knee":"bad","ankle":"null"}' \
  http://54.86.161.187:8080/internal/ai/status | jq

# 6) FastAPI에서 세션 발급 (FastAPI 서버 직접 호출)
curl -s -X POST \
  "https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io/api/session?side=auto"

# 6-1) Spring에 FastAPI 세션 등록
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"user123","fastApiSessionId":"session_7f83a1f3"}' \
  http://54.86.161.187:8080/api/session | jq

# 7) FastAPI에서 피드백 전송 테스트 (내부 엔드포인트)
# 주의: {fastApiSessionId}는 FastAPI에서 받은 세션 ID를 사용해야 합니다
curl -s -X POST -H "Content-Type: application/json" \
  -d '{
    "type": "analysis",
    "frameNumber": 1,
    "state": "SIT",
    "side": "left",
    "squatCount": 5,
    "checks": {"back": "good", "knee": "too forward", "ankle": "good"},
    "timestamp": 1730892345000
  }' \
  http://54.86.161.187:8080/api/internal/inference/{fastApiSessionId}/feedback | jq

# 9) 세션 완료
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"framesIn":150,"framesOut":150,"durationSeconds":30}' \
  http://54.86.161.187:8080/api/session/{sessionId}/finish | jq
```

---

## 8) FastAPI Squat AI Service 연동 가이드 (REST API 기반)

### 8.1 아키텍처 개요

* **Spring (컨트롤 플레인)**: 세션 매핑, 피드백 중계, 레이트리밋, 로깅
  - **역할**: 프론트에서 받은 FastAPI 세션 ID를 저장하고, 피드백을 앱으로 전달 (STOMP 웹소켓)
  - **세션 저장**: 프론트엔드가 FastAPI에서 발급받은 세션 ID를 백엔드에 등록
  
* **FastAPI Squat AI Service (데이터 플레인)**: REST API 기반 영상 분석 처리
  - **역할**: REST API로 세션 발급, 프레임 업로드 받기, 분석 수행, 분석 결과 반환
  - **Base URL**: `https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io`
  - **프로토콜**: REST API (multipart/form-data 파일 업로드)
  
* **iOS 앱**: FastAPI에서 직접 세션 발급 후, FastAPI REST API에 **직접** 프레임 업로드
  - **1단계**: FastAPI에서 세션 발급 (`POST /api/session`)
  - **2단계**: Spring에 FastAPI 세션 ID 등록 (`POST /api/session`)
  - **3단계**: FastAPI REST API에 프레임 업로드 (`POST /api/session/{fastApiSessionId}/frame`)
  - **4단계**: FastAPI 분석 결과를 Spring으로 전송 (FastAPI에서 자동 처리)
  - **5단계**: Spring이 STOMP 웹소켓으로 앱에 피드백 전달

### 8.1.1 연결 흐름도

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│ iOS 앱  │                    │ Spring  │                    │ FastAPI │
└────┬────┘                    └────┬────┘                    └────┬────┘
     │                              │                              │
     │  1. STOMP 웹소켓 연결         │                              │
     │─────────────────────────────>│                              │
     │                              │                              │
     │  2. POST /api/session?side=auto (FastAPI에서 세션 발급)      │
     │────────────────────────────────────────────────────────────>│
     │  3. "session_7f83a1f3"       │                              │
     │<────────────────────────────────────────────────────────────│
     │                              │                              │
     │  4. POST /api/session (FastAPI 세션 ID를 Spring에 등록)     │
     │─────────────────────────────>│                              │
     │  5. {sessionId, fastApiUrl: null, fastApiSessionId}        │
     │<─────────────────────────────│                              │
     │                              │                              │
     │  6. POST /api/session/{fastApiSessionId}/frame (프레임 업로드)│
     │────────────────────────────────────────────────────────────>│
     │                              │                              │
     │  7. 분석 결과 응답            │                              │
     │<────────────────────────────────────────────────────────────│
     │                              │  8. POST /internal/inference/{fastApiSessionId}/feedback│
     │                              │<─────────────────────────────│
     │  9. STOMP로 피드백 전달       │                              │
     │<─────────────────────────────│                              │
     │                              │                              │
     │  10. POST /api/session/{springSessionId}/finish (세션 종료 시)│
     │─────────────────────────────>│                              │
```

**핵심 포인트**:
- ✅ 앱이 **FastAPI에서 직접 세션 발급** 받음
- ✅ Spring은 **FastAPI 세션 ID를 저장하고 피드백 중계** 담당
- ✅ 앱 → FastAPI는 **REST API로 직접 프레임 업로드** (Spring 경유 없음)
- ✅ FastAPI → Spring: 분석 결과를 HTTP POST로 전송
- ✅ Spring → 앱: STOMP 웹소켓으로 피드백 전달

### 8.2 FastAPI 세션 발급 및 Spring 등록

#### 8.2.1 FastAPI에서 세션 발급

* **POST** `https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io/api/session?side=auto`
* **FastAPI Base URL**: `https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io`
* **요청**: 쿼리 파라미터
  - `side`: "auto", "left", "right" (선택사항, 기본값: "auto")

* **응답 200**:
```json
"session_7f83a1f3"  // FastAPI 세션 ID (문자열)
```

#### 8.2.2 Spring에 FastAPI 세션 등록

* **POST** `/api/session`
* **요청**:
```json
{ 
  "userId": "user123",  // 게스트 ID (선택사항, 기본값: "guest")
  "fastApiSessionId": "session_7f83a1f3"  // FastAPI에서 발급받은 세션 ID (필수)
}
```

* **응답 200**:
```json
{
  "sessionId": "e0e1c6af-...",  // Spring 세션 ID (백엔드 내부 관리용)
  "fastApiUrl": null,  // 더 이상 사용하지 않음
  "fastApiSessionId": "session_7f83a1f3"  // FastAPI 세션 ID
}
```

**설명**:
- `sessionId`: Spring 세션 ID (백엔드 내부 관리용, 세션 완료 시 사용)
- `fastApiUrl`: null (더 이상 사용하지 않음)
- `fastApiSessionId`: FastAPI 세션 ID (프레임 업로드 시 경로에 사용)

#### 8.2.3 세션 완료

* **POST** `/api/session/{sessionId}/finish`
* **요청**:
```json
{
  "framesIn": 150,
  "framesOut": 150,
  "durationSeconds": 30
}
```

* **응답 200**:
```json
{
  "status": "completed",
  "sessionId": "e0e1c6af-..."
}
```

### 8.3 FastAPI REST API 프레임 업로드

#### 8.3.1 프레임 업로드 방법

**중요**: FastAPI에서 직접 세션을 발급받고, **앱에서 FastAPI REST API에 직접 프레임 업로드**합니다.

**전체 흐름**:
1. **FastAPI에서 세션 발급**: `POST https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io/api/session?side=auto`
2. **Spring에 세션 등록**: `POST /api/session` (FastAPI 세션 ID 전달)
3. **FastAPI에 프레임 업로드**: `POST https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io/api/session/{fastApiSessionId}/frame` (multipart/form-data)

* **URL**: `https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io/api/session/{fastApiSessionId}/frame`
* **프로토콜**: REST API (multipart/form-data)
* **인증**: 현재 v1.0.0 기준 인증 없음
* **연결 경로**: `앱 → FastAPI (직접)` (Spring 거치지 않음)

**요청 형식**:
- Content-Type: `multipart/form-data`
- 파일 필드명: `file`
- 파일 형식: 이미지 (JPEG, PNG 등)

**응답 형식**:
```json
{
  "state": "SIT",  // "SIT", "STAND" 등
  "side": "left",  // "left", "right"
  "squat_count": 5,  // 스쿼트 카운트
  "checks": {
    "back": "good",
    "knee": "too forward",
    "ankle": "good"
  }
}
```

**핵심 정리**:
- ✅ 앱: FastAPI에서 직접 세션 발급 받기
- ✅ Spring: FastAPI 세션 ID 저장 및 피드백 중계 (STOMP 웹소켓)
- ✅ FastAPI: REST API로 세션 발급, 프레임 업로드 받기, 분석 수행, Spring으로 결과 전송
- ✅ 앱: FastAPI REST API에 직접 프레임 업로드
- ✅ 피드백: FastAPI → Spring → 앱 (STOMP 웹소켓)

### 8.5 FastAPI 서버에서 Spring으로 피드백 전송 구현 예시 (Python)

```python
from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.responses import JSONResponse
import httpx
import time
from typing import Optional

app = FastAPI()

# Spring 백엔드 URL
SPRING_BACKEND_URL = "http://54.86.161.187:8080"

async def send_feedback_to_spring(fast_api_session_id: str, analysis_result: dict):
    """Spring 백엔드로 피드백 전송"""
    url = f"{SPRING_BACKEND_URL}/api/internal/inference/{fast_api_session_id}/feedback"
    try:
        async with httpx.AsyncClient() as client:
            # FastAPI 분석 결과를 Spring 피드백 형식으로 변환
            feedback_data = {
                "type": "analysis",
                "state": analysis_result.get("state"),
                "side": analysis_result.get("side"),
                "squatCount": analysis_result.get("squat_count"),
                "checks": analysis_result.get("checks", {}),
                "timestamp": int(time.time() * 1000)
            }
            
            response = await client.post(url, json=feedback_data, timeout=5.0)
            if response.status_code == 200:
                print(f"피드백 전송 성공: fastApiSessionId={fast_api_session_id}")
                return True
            else:
                print(f"피드백 전송 실패: fastApiSessionId={fast_api_session_id}, status={response.status_code}")
                return False
    except Exception as e:
        print(f"피드백 전송 오류: fastApiSessionId={fast_api_session_id}, error={e}")
        return False

@app.post("/api/session/{session_id}/frame")
async def upload_frame(
    session_id: str,
    file: UploadFile = File(...),
    include_landmarks: bool = False,
    include_debug: bool = False
):
    """
    프레임 업로드 및 분석
    
    FastAPI 분석 결과를 응답으로 반환하고,
    동시에 Spring 백엔드로 피드백 전송
    """
    try:
        # 이미지 파일 읽기
        image_data = await file.read()
        
        # ═══════════════════════════════════════════════════════
        # 실제 AI 분석 로직 수행 (예시)
        # ═══════════════════════════════════════════════════════
        analysis_result = {
            "state": "SIT",  # "SIT", "STAND" 등
            "side": "left",  # "left", "right"
            "squat_count": 5,  # 스쿼트 카운트
            "checks": {
                "back": "good",
                "knee": "too forward",
                "ankle": "good"
            }
        }
        
        # ═══════════════════════════════════════════════════════
        # 분석 결과를 Spring 백엔드로 전송 (앱으로 피드백 전달)
        # ═══════════════════════════════════════════════════════
        # 비동기로 전송 (응답을 블로킹하지 않음)
        import asyncio
        asyncio.create_task(send_feedback_to_spring(session_id, analysis_result))
        
        # 분석 결과를 응답으로 반환 (앱이 직접 받을 수도 있음)
        return JSONResponse(content=analysis_result)
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"분석 오류: {str(e)}")
```

**핵심 변경사항**:
- ✅ FastAPI에서 분석 결과를 Spring 백엔드로 HTTP POST 전송 (비동기)
- ✅ Spring이 STOMP 웹소켓을 통해 앱에 피드백 전달
- ✅ 분석 결과는 FastAPI 응답 형식 그대로 전송: `state`, `side`, `squatCount`, `checks`
- ✅ Spring에서 FastAPI `checks`를 기존 `ai` 형식으로 변환하여 앱에 전달

**피드백 전송 형식**:

FastAPI에서 Spring으로 전송하는 형식:
```json
{
  "type": "analysis",
  "state": "SIT",
  "side": "left",
  "squatCount": 5,
  "checks": {
    "back": "good",
    "knee": "too forward",
    "ankle": "good"
  },
  "timestamp": 1730892345000
}
```

Spring에서 앱으로 전송하는 STOMP 메시지 형식:
```json
{
  "type": "DATA",
  "payload": {
    "ts": 1730892345000,
    "state": "SIT",
    "side": "left",
    "squatCount": 5,
    "checks": {
      "back": "good",
      "knee": "too forward",
      "ankle": "good"
    },
    "ai": {
      "lumbar": "good",
      "knee": "bad",
      "ankle": "good"
    }
  }
}
```

### 8.5.1 피드백 전달 흐름

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│ iOS 앱  │                    │ Spring  │                    │ FastAPI │
└────┬────┘                    └────┬────┘                    └────┬────┘
     │                              │                              │
     │  1. STOMP 웹소켓 연결         │                              │
     │─────────────────────────────>│                              │
     │                              │                              │
     │  2. POST /api/session?side=auto (FastAPI에서 세션 발급)      │
     │────────────────────────────────────────────────────────────>│
     │  3. "session_7f83a1f3"       │                              │
     │<────────────────────────────────────────────────────────────│
     │                              │                              │
     │  4. POST /api/session (FastAPI 세션 ID를 Spring에 등록)     │
     │─────────────────────────────>│                              │
     │  5. {sessionId, fastApiUrl: null, fastApiSessionId}         │
     │<─────────────────────────────│                              │
     │                              │                              │
     │  6. POST /api/session/{fastApiSessionId}/frame (프레임 업로드)│
     │────────────────────────────────────────────────────────────>│
     │                              │                              │
     │  7. 분석 결과 응답 (직접 수신) │                              │
     │<────────────────────────────────────────────────────────────│
     │                              │  8. POST /internal/inference/{fastApiSessionId}/feedback│
     │                              │<─────────────────────────────│
     │  9. STOMP로 피드백 전달       │                              │
     │<─────────────────────────────│                              │
```

**핵심 포인트**:
- ✅ 앱 → FastAPI: 세션 발급 요청 (REST API)
- ✅ FastAPI → 앱: 세션 ID 응답 (문자열)
- ✅ 앱 → Spring: FastAPI 세션 ID 등록 (REST API)
- ✅ 앱 → FastAPI: 비디오 프레임 전송 (REST API, multipart/form-data)
- ✅ FastAPI → 앱: 분석 결과 응답 (HTTP 응답, 직접 수신)
- ✅ FastAPI → Spring: 분석 결과 전송 (HTTP POST, 비동기)
- ✅ Spring → 앱: 피드백 전달 (STOMP 웹소켓)
- ✅ 앱은 STOMP 웹소켓(`/ws`)만 연결하면 됨 (FastAPI는 REST API)

### 8.6 운영 체크리스트

* **연결 경로 명확화**: 
  - ✅ 앱: FastAPI에서 직접 세션 발급, FastAPI REST API에 직접 프레임 업로드
  - ✅ Spring API: FastAPI 세션 ID 저장, 세션 매핑, 피드백 중계 담당
  - ✅ FastAPI: REST API로 세션 발급, 프레임 업로드 받기, 분석 수행, Spring으로 결과 전송
  - ✅ Spring: FastAPI에서 받은 피드백을 STOMP 웹소켓으로 앱에 전달
  - ✅ 앱: STOMP 웹소켓으로 피드백 수신
  
* **레이트리밋**: Spring에서 세션별 `frames_in/out`, `duration_s` 집계
* **로깅/관찰성**: 
  - Spring sessionId와 FastAPI sessionId 매핑으로 로그 상관추적
  - FastAPI에서 Spring으로 피드백 전송 실패 시 재시도 로직 고려
* **LB/Nginx**: 
  - FastAPI 서버 앞에 LB 구성 시 일반 HTTP/HTTPS 라우팅만 필요
  - 세션 고정(해시 라우팅)으로 동일 세션은 동일 서버로 라우팅 (선택사항)
* **네트워크 설정**: 
  - iOS 앱에서 FastAPI 서버 URL에 직접 접근 가능해야 함
  - FastAPI에서 Spring 서버로 HTTP 요청 가능해야 함
  - 방화벽/보안 그룹에서 FastAPI HTTP/HTTPS 포트 오픈 확인
* **에러 처리**: 
  - FastAPI 분석 실패 시 에러 응답 반환
  - Spring 피드백 전송 실패 시 로그 기록 (앱은 직접 응답 받음)

### 8.7 환경 변수 설정

```bash
# Spring application.yml 또는 환경 변수
FASTAPI_BASE_URL=https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io
```

**주의사항**:
- `FASTAPI_BASE_URL`은 **FastAPI 서버의 실제 주소**여야 합니다
- iOS 앱이 이 주소에 직접 접근할 수 있어야 합니다
- FastAPI 서버에서 Spring 서버로 HTTP 요청 가능해야 합니다
- 개발 환경: `http://localhost:8000` (로컬 테스트용)
- 운영 환경: `https://squat-api.your-domain.com` (도메인 + HTTPS 권장)

### 8.8 FAQ

**Q: 누가 FastAPI 세션을 생성하나요?**  
A: **앱이 직접 FastAPI에서 생성합니다**. 
1. 앱이 FastAPI API(`POST https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io/api/session?side=auto`)로 세션 발급 요청
2. FastAPI가 세션 ID 반환 (`"session_7f83a1f3"`)
3. 앱이 Spring API(`POST /api/session`)로 FastAPI 세션 ID를 전달하여 등록
4. Spring이 FastAPI sessionId를 받아서 Spring sessionId와 매핑하여 저장
5. 앱에 `sessionId`(Spring 세션 ID), `fastApiUrl`(null), `fastApiSessionId` 반환

**Q: 앱에서 FastAPI에 어떻게 프레임을 업로드하나요?**  
A: **REST API로 직접 업로드합니다**.
1. FastAPI에서 받은 `fastApiSessionId` 사용 (또는 Spring 등록 시 받은 `fastApiSessionId`)
2. `POST https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io/api/session/{fastApiSessionId}/frame`으로 multipart/form-data 업로드
3. FastAPI가 분석 결과를 HTTP 응답으로 반환

**Q: FastAPI 서버가 다운되면?**  
A: 앱은 FastAPI REST API에 직접 요청하므로, FastAPI 다운 시 HTTP 에러가 발생합니다. 에러 처리 및 재시도 로직 구현 필요.

**Q: 세션 완료는 언제 호출하나요?**  
A: 사용자가 분석을 종료할 때 Spring API(`POST /api/session/{id}/finish`)를 호출해 통계를 저장합니다. FastAPI 세션은 자동으로 정리되거나 명시적으로 삭제할 수 있습니다.

**Q: Spring 서버와 FastAPI 서버가 다른 도메인이어도 되나요?**  
A: **네, 가능합니다**. 앱이 FastAPI에서 직접 세션을 발급받고, Spring은 세션 저장 및 피드백 중계만 합니다. FastAPI는 별도 서버/도메인에서 운영 가능하며, FastAPI에서 Spring으로 피드백을 전송할 수 있도록 네트워크 접근이 가능해야 합니다.

**Q: FastAPI에서 분석 결과를 어떻게 앱으로 전달하나요?**  
A: **두 가지 방법이 있습니다**.
1. **직접 응답**: FastAPI가 분석 결과를 HTTP 응답으로 바로 반환 (앱이 직접 수신)
2. **Spring 경유**: FastAPI가 분석 결과를 Spring으로 전송 → Spring이 STOMP 웹소켓으로 앱에 피드백 전달 (비동기)

**Q: 앱에서 STOMP 웹소켓 연결이 필요한가요?**  
A: **네, 필요합니다**. Spring에서 피드백을 받기 위해 STOMP 웹소켓(`ws://spring-server:8080/ws`)에 연결해야 합니다. FastAPI는 REST API이므로 웹소켓 연결이 필요 없습니다.

**Q: FastAPI에서 Spring으로 피드백을 보낼 때 인증이 필요하나요?**  
A: **현재는 인증 없이 접근 가능합니다**. 운영 환경에서는 FastAPI 서버의 IP를 화이트리스트에 추가하거나 API 키를 사용하는 것을 권장합니다.

**Q: FastAPI 분석 결과 형식은 어떻게 되나요?**  
A: **FastAPI Squat AI Service v1.0.0 형식**:
```json
{
  "state": "SIT",
  "side": "left",
  "squat_count": 5,
  "checks": {
    "back": "good",
    "knee": "too forward",
    "ankle": "good"
  }
}
```
Spring이 이를 기존 `ai` 형식(`lumbar`, `knee`, `ankle`)으로 변환하여 앱에 전달합니다.

---

## 9) 백엔드 구현 상세

### 9.1 아키텍처 개요

**Spring Boot 백엔드 구조**:
- **프레임워크**: Spring Boot (Java)
- **데이터베이스**: MySQL
- **WebSocket**: STOMP (Spring WebSocket Message Broker)
- **인증**: 게스트 모드 (사용자 인증 없음, JWT는 FastAPI 통신용으로만 사용)
- **외부 연동**: FastAPI Squat AI Service (REST API)

### 9.2 주요 컴포넌트

#### 9.2.1 컨트롤러 (Controllers)

* **InternalSessionController** (`/api`, `/internal/session`)
  - 게스트 세션 발급: `POST /internal/session`
  - FastAPI 세션 등록: `POST /api/session` (프론트에서 받은 FastAPI 세션 ID 저장)
  - 세션 완료: `POST /api/session/{sessionId}/finish`
  - FastAPI 피드백 수신: `POST /api/internal/inference/{fastApiSessionId}/feedback`

* **FSRController** (`/api/fsr_data`)
  - FSR 데이터 업로드: `POST /api/fsr_data`
  - 최신 스냅샷: `GET /api/fsr_data/latest`
  - 피드백: `GET /api/fsr_data/feedback`
  - 통합 피드백: `GET /api/fsr_data/feedback/combined`

* **AiInputController** (`/internal/ai`)
  - AI 상태 입력: `POST /internal/ai/status`

* **SessionWsController** (STOMP)
  - 메시지 수신: `/app/session.message`
  - 메시지 전송: `/user/queue/session`

#### 9.2.2 서비스 (Services)

* **InferenceSessionService**
  - Spring 세션과 FastAPI 세션 매핑 관리
  - 프론트에서 받은 FastAPI 세션 ID 저장
  - 세션 완료 처리
  - 세션 TTL: 30분

* **InferenceFeedbackService**
  - FastAPI에서 받은 피드백을 앱으로 전달 (STOMP)
  - FastAPI `checks`를 기존 `ai` 형식으로 변환
  - 피드백 메시지 25자 제한 처리

* **FastApiSessionService**
  - (더 이상 사용하지 않음 - 앱이 FastAPI에서 직접 세션 발급)

* **FSRDataService**
  - FSR 데이터 저장 및 관리 (인메모리)
  - 10초 윈도우 평균 계산
  - WebSocket 브로드캐스트

* **UnifiedFeedbackService**
  - AI + FSR 통합 피드백 생성
  - 피드백 메시지 병합 및 25자 제한

* **JwtService**
  - JWT 토큰 생성/검증 (FastAPI 통신용으로만 사용)
  - 토큰 만료: 24시간

#### 9.2.3 WebSocket 설정

* **STOMP WebSocket** (`/ws`)
  - 엔드포인트: `ws://54.86.161.187:8080/ws?token=<wsToken>`
  - 게스트 모드: 토큰 없이도 연결 가능 (게스트 ID 자동 생성)
  - Principal 설정 (WsPrincipalHandshakeHandler)
  - 구독: `/user/queue/session`
  - 송신: `/app/session.message`

* **FSR WebSocket** (`/ws/fsr-data`)
  - 엔드포인트: `ws://54.86.161.187:8080/ws/fsr-data`
  - 순수 WebSocket (STOMP 아님)
  - JSON 형식으로 FSR 데이터 브로드캐스트

#### 9.2.4 데이터베이스

* **MySQL 스키마**:
  - `WsSession`: WebSocket 세션 정보
  - `WsMessageLog`: WebSocket 메시지 로그
  - `WsIdentity`: WebSocket 인증 정보

#### 9.2.5 설정 파일

* **application.yml**:
  - 서버 포트: 8080
  - 데이터베이스: MySQL (54.86.161.187:3306)
  - JWT 설정: secret, expiration (FastAPI 통신용으로만 사용)
  - FastAPI Base URL: 환경 변수 또는 기본값

### 9.3 피드백 처리 흐름

1. **FastAPI → Spring**:
   - FastAPI가 분석 완료 후 `POST /api/internal/inference/{fastApiSessionId}/feedback` 호출
   - Spring이 `fastApiSessionId`로 `springSessionId` 조회
   - `InferenceFeedbackService`가 피드백 변환 및 전송

2. **Spring → 앱**:
   - STOMP WebSocket으로 `/user/queue/session`에 메시지 전송
   - 메시지 타입: `DATA` (분석 결과), `voice` (피드백 텍스트)
   - FastAPI `checks`를 `ai` 형식으로 변환하여 포함

3. **피드백 변환 규칙**:
   - `checks.back` → `ai.lumbar`
   - `checks.knee` → `ai.knee`
   - `checks.ankle` → `ai.ankle`
   - 값 정규화: "good" → "good", "too forward"/"bad" → "bad", 기타 → "null"

### 9.4 세션 관리

* **Spring 세션**:
  - 세션 ID: UUID 형식
  - TTL: 30분
  - 상태: ACTIVE, COMPLETED, EXPIRED
  - FastAPI 세션 ID 매핑 저장

* **FastAPI 세션**:
  - 세션 ID: "session_xxxxx" 형식
  - 앱이 FastAPI에서 직접 세션 발급
  - 앱이 직접 FastAPI REST API에 프레임 업로드

### 9.5 에러 처리

* **FastAPI 피드백 수신 실패**:
  - 세션을 찾을 수 없음: 400 Bad Request
  - 피드백 전달 실패: 400 Bad Request (로그 기록)

* **세션 관리**:
  - 세션 만료: 400 Bad Request
  - 세션 없음: 400 Bad Request

* **FSR 데이터**:
  - 검증 실패: 400 Bad Request (ratio1~6 ∈ [0,100])

### 9.6 로깅

* **주요 로그 포인트**:
  - 세션 생성/완료
  - FastAPI 피드백 수신/전송
  - FSR 데이터 업데이트
  - WebSocket 연결/해제

* **로그 레벨**:
  - 애플리케이션: DEBUG/INFO/ERROR

### 9.7 환경 변수

```bash
# FastAPI 연동
FASTAPI_BASE_URL=https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io

# JWT (FastAPI 통신용으로만 사용)
JWT_SECRET=mySecretKey123456789012345678901234567890
JWT_EXPIRATION=86400000  # 24시간
```

### 9.8 배포 정보

* **서버**: AWS EC2 (54.86.161.187)
* **포트**: 8080
* **데이터베이스**: MySQL (54.86.161.187:3306)
* **빌드**: Gradle
* **Java 버전**: (build.gradle 확인 필요)
