
# Squirret Backend 테스트 가이드

## 1. 기본 정보
- Base URL: `http://54.86.161.187:8080`
- Stomp WS: `ws://54.86.161.187:8080/ws`
- Pure WebSocket(FSR): `ws://54.86.161.187:8080/ws/fsr-data`
- Content-Type: `application/json; charset=utf-8`
- 인증: 별도 로그인 없이 사용하는 게스트 모드이며, REST API는 공개되어 있고 STOMP 연결 시 서버가 자동으로 게스트 ID를 발급합니다.

## 2. 테스트 체크리스트
### 2.1 1초 측정값 & 10초 피드백 확인
1. `POST /api/guest/session`으로 게스트 ID 확보.
2. STOMP 클라이언트에서 `/ws` 접속 후 `/user/queue/session` 구독.
3. 1초 주기로 `{"type":"DATA","payload":{...}}` 메시지가 도착하는지 확인.
4. 10초 주기로 `{"type":"voice","text":"..."}` 메시지가 오는지 확인.
5. 필요한 경우 `/internal/ai/status`에 AI 상태를 넣어 메시지가 바뀌는지 확인.

### 2.2 FSR 데이터 흐름 검증
1. `POST /api/fsr_data`로 좌/우 데이터를 업로드.
2. `GET /api/fsr_data/latest`에서 최신 스냅샷이 갱신됐는지 확인.
3. `GET /api/fsr_data/feedback`에서 10초 평균 기반 피드백을 확인.
4. `ws://.../ws/fsr-data`에 연결해 실시간 스트림이 브로드캐스트되는지 확인.

### 2.3 FastAPI 연동
1. FastAPI에 `POST https://squat-api.blackmoss-f506213d.koreacentral.azurecontainerapps.io/api/session` 요청 → 세션 ID 확보.
2. 확보한 `fastApiSessionId`와 게스트 ID를 `POST /api/session`으로 등록.
3. FastAPI에 프레임 업로드 → FastAPI가 Spring으로 `POST /api/internal/inference/{fastApiSessionId}/feedback` 호출.
4. STOMP 구독 중인 클라이언트에서 분석 `DATA` 메시지/`voice` 메시지가 도착하는지 확인.
5. 종료 시 `POST /api/session/{sessionId}/finish`로 정리.

## 3. 주요 REST 엔드포인트 요약
- `POST /api/guest/session` : 게스트 세션 발급.
- `GET /api/guest/health` : 헬스 체크.
- `POST /api/fsr_data` : FSR 데이터 업로드.
- `GET /api/fsr_data/latest` : 최신 FSR 스냅샷.
- `GET /api/fsr_data/feedback` : 10초 평균 피드백.
- `GET /api/fsr_data/feedback/combined` : AI + FSR 통합 피드백.
- `POST /internal/ai/status` : AI 상태 수동 입력.
- `POST /api/session` : FastAPI 세션 등록.
- `POST /api/internal/inference/{fastApiSessionId}/feedback` : FastAPI 분석 결과 수신.
- `POST /api/session/{sessionId}/finish` : 세션 종료 통계 저장.
