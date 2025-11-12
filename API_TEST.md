# API 테스트 가이드

## 1. Swagger UI로 테스트 (가장 쉬운 방법)

### 접속
- **로컬**: http://localhost:8080/swagger-ui/index.html
- **프로덕션**: http://54.86.161.187:8080/swagger-ui/index.html

### 사용 방법
1. 브라우저에서 위 URL 접속
2. 왼쪽 사이드바에서 원하는 API 클릭
3. "Try it out" 버튼 클릭
4. 필요한 파라미터 입력
5. "Execute" 버튼 클릭
6. 응답 결과 확인

---

## 2. cURL로 빠른 테스트

### 기본 헬스 체크
```bash
# 서버 상태 확인
curl http://localhost:8080/api/guest/health

# 서버 메시지 확인
curl http://localhost:8080/api/guest/
```

### 게스트 세션 생성
```bash
# 게스트 세션 생성
curl -X POST http://localhost:8080/api/guest/session

# 응답 예시:
# {"guestId":"a1b2c3d4-e5f6-7890-1234-567890abcdef","message":"게스트 세션이 생성되었습니다."}
```

### 게스트 세션 조회
```bash
# 위에서 받은 guestId 사용
curl http://localhost:8080/api/guest/session/a1b2c3d4-e5f6-7890-1234-567890abcdef
```

### STOMP용 세션 발급
```bash
curl -X POST http://localhost:8080/internal/session
```

### FSR 데이터 업로드
```bash
curl -X POST http://localhost:8080/api/fsr_data \
  -H "Content-Type: application/json" \
  -d '{
    "side": "left",
    "ratio1": 15.2,
    "ratio2": 7.5,
    "ratio3": 3.1,
    "ratio4": 25.4,
    "ratio5": 30.0,
    "ratio6": 18.8
  }'
```

### FSR 최신 데이터 조회
```bash
curl http://localhost:8080/api/fsr_data/latest
```

### FSR 피드백 조회
```bash
curl http://localhost:8080/api/fsr_data/feedback
```

### 통합 피드백 조회
```bash
curl http://localhost:8080/api/fsr_data/feedback/combined
```

### FastAPI 세션 등록
```bash
curl -X POST http://localhost:8080/api/session \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "guest123",
    "fastApiSessionId": "session_test_123"
  }'
```

### 세션 완료
```bash
# 위에서 받은 sessionId 사용
curl -X POST http://localhost:8080/api/session/{sessionId}/finish \
  -H "Content-Type: application/json" \
  -d '{
    "framesIn": 150,
    "framesOut": 150,
    "durationSeconds": 30
  }'
```

### AI 상태 입력 (내부)
```bash
curl -X POST http://localhost:8080/internal/ai/status \
  -H "Content-Type: application/json" \
  -d '{
    "lumbar": "good",
    "knee": "bad",
    "ankle": "null"
  }'
```

---

## 3. 전체 테스트 스크립트

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"

echo "=== 1. 헬스 체크 ==="
curl -s $BASE_URL/api/guest/health | jq
echo ""

echo "=== 2. 게스트 세션 생성 ==="
GUEST_RESPONSE=$(curl -s -X POST $BASE_URL/api/guest/session)
echo $GUEST_RESPONSE | jq
GUEST_ID=$(echo $GUEST_RESPONSE | jq -r '.guestId')
echo "Guest ID: $GUEST_ID"
echo ""

echo "=== 3. 게스트 세션 조회 ==="
curl -s $BASE_URL/api/guest/session/$GUEST_ID | jq
echo ""

echo "=== 4. STOMP 세션 발급 ==="
curl -s -X POST $BASE_URL/internal/session | jq
echo ""

echo "=== 5. FSR 데이터 업로드 (왼발) ==="
curl -s -X POST $BASE_URL/api/fsr_data \
  -H "Content-Type: application/json" \
  -d '{"side":"left","ratio1":15.2,"ratio2":7.5,"ratio3":3.1,"ratio4":25.4,"ratio5":30.0,"ratio6":18.8}' | jq
echo ""

echo "=== 6. FSR 데이터 업로드 (오른발) ==="
curl -s -X POST $BASE_URL/api/fsr_data \
  -H "Content-Type: application/json" \
  -d '{"side":"right","ratio1":12.3,"ratio2":8.4,"ratio3":5.1,"ratio4":20.0,"ratio5":32.5,"ratio6":21.7}' | jq
echo ""

echo "=== 7. 최신 FSR 데이터 조회 ==="
curl -s $BASE_URL/api/fsr_data/latest | jq
echo ""

echo "=== 8. FSR 피드백 조회 ==="
curl -s $BASE_URL/api/fsr_data/feedback | jq
echo ""

echo "=== 9. 통합 피드백 조회 ==="
curl -s $BASE_URL/api/fsr_data/feedback/combined | jq
echo ""

echo "=== 10. FastAPI 세션 등록 ==="
SESSION_RESPONSE=$(curl -s -X POST $BASE_URL/api/session \
  -H "Content-Type: application/json" \
  -d '{"userId":"guest123","fastApiSessionId":"session_test_123"}')
echo $SESSION_RESPONSE | jq
SPRING_SESSION_ID=$(echo $SESSION_RESPONSE | jq -r '.sessionId')
echo "Spring Session ID: $SPRING_SESSION_ID"
echo ""

echo "=== 11. AI 상태 입력 ==="
curl -s -X POST $BASE_URL/internal/ai/status \
  -H "Content-Type: application/json" \
  -d '{"lumbar":"good","knee":"bad","ankle":"null"}' | jq
echo ""

echo "=== 12. 세션 완료 ==="
curl -s -X POST $BASE_URL/api/session/$SPRING_SESSION_ID/finish \
  -H "Content-Type: application/json" \
  -d '{"framesIn":150,"framesOut":150,"durationSeconds":30}' | jq
echo ""

echo "=== 테스트 완료 ==="
```

---

## 4. Postman으로 테스트

1. Postman 열기
2. Import → Link
3. `http://localhost:8080/v3/api-docs` 입력
4. Import 클릭
5. 자동으로 모든 API가 컬렉션으로 추가됨
6. 각 API를 클릭하여 테스트

---

## 5. 빠른 확인 체크리스트

### ✅ 기본 동작 확인
- [ ] `GET /api/guest/health` → `{"status":"ok","mode":"guest"}`
- [ ] `POST /api/guest/session` → `{"guestId":"...","message":"..."}`
- [ ] `GET /api/guest/session/{guestId}` → `{"guestId":"...","valid":true}`

### ✅ FSR 데이터 확인
- [ ] `POST /api/fsr_data` (왼발 데이터) → `{"status":"ACCEPTED"}`
- [ ] `POST /api/fsr_data` (오른발 데이터) → `{"status":"ACCEPTED"}`
- [ ] `GET /api/fsr_data/latest` → 양발 데이터 포함
- [ ] `GET /api/fsr_data/feedback` → 피드백 메시지 포함

### ✅ 세션 관리 확인
- [ ] `POST /internal/session` → `{"sessionId":"...","wsToken":"..."}`
- [ ] `POST /api/session` (FastAPI 세션 등록) → `{"sessionId":"...","fastApiSessionId":"..."}`

---

## 6. 문제 해결

### 서버가 응답하지 않는 경우
```bash
# 컨테이너 상태 확인
docker-compose ps

# 로그 확인
docker logs squirret-app --tail 50

# 컨테이너 재시작
docker-compose restart app
```

### 404 에러가 나는 경우
- URL 경로 확인 (`/api/guest/` vs `/api/guest`)
- Swagger UI에서 정확한 경로 확인

### 400 에러가 나는 경우
- 요청 본문 형식 확인 (JSON)
- 필수 파라미터 누락 확인
- 데이터 타입 및 범위 확인 (예: ratio1~6은 0~100)

---

## 7. 실시간 모니터링

```bash
# 로그 실시간 확인
docker logs -f squirret-app

# 특정 API 호출만 필터링
docker logs squirret-app | grep "GET\|POST"
```

