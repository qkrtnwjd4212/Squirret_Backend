# Squirret Backend - Guest Mode

이 프로젝트는 Spring Boot 3.4.x와 Java 17을 사용한 게스트 전용 백엔드 애플리케이션입니다. 사용자 인증 없이 모든 기능을 사용할 수 있습니다.

## 주요 기능

- 게스트 세션 관리
- 피트니스 데이터 CRUD
- FSR 데이터 수집 및 피드백
- AI 추론 세션 관리
- WebSocket 통신

## 프로젝트 구조

```
src/main/java/com/squirret/squirretbackend/
├── controller/
│   ├── GuestController.java          # 게스트 세션 관리 API
│   ├── FitnessDataController.java    # 피트니스 데이터 CRUD
│   ├── FSRController.java            # FSR 데이터 및 피드백
│   ├── InternalSessionController.java # AI 추론 세션 관리
│   └── ...
├── entity/
│   ├── WsSession.java                # WebSocket 세션 엔티티
│   ├── WsIdentity.java               # WebSocket 식별자
│   └── ...
├── service/
│   ├── InferenceSessionService.java  # AI 추론 세션 서비스
│   ├── FSRDataService.java           # FSR 데이터 서비스
│   └── ...
└── dto/
    └── ...
```

## 설정 방법

### 1. 데이터베이스 설정

MySQL 데이터베이스를 사용합니다. `application.yml`에서 데이터베이스 연결 정보를 설정하세요.

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/squirretDB
    username: root
    password: your_password
```

### 2. 환경 변수 (선택사항)

FastAPI 연동을 위한 환경 변수:

```bash
export FASTAPI_BASE_URL="https://your-fastapi-url.com"
export INFERENCE_WS_BASE_URL="ws://localhost:8000"
```

## 실행 방법

```bash
# 의존성 설치
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

또는 Docker Compose 사용:

```bash
docker-compose up -d
```

## API 엔드포인트

### 게스트 세션 관리
- `GET /api/guest/` - 서버 상태 확인
- `POST /api/guest/session` - 게스트 세션 생성
- `GET /api/guest/session/{guestId}` - 게스트 세션 조회
- `GET /api/guest/health` - 헬스 체크

### 피트니스 데이터
- `POST /api/fitness/save` - 피트니스 데이터 저장
- `GET /api/fitness/{userId}` - 사용자 피트니스 데이터 조회
- `GET /api/fitness/{userId}/workouts/{workoutId}` - 운동 기록 조회
- `PUT /api/fitness/{userId}` - 피트니스 데이터 업데이트
- `PUT /api/fitness/{userId}/workouts/{workoutId}` - 운동 기록 업데이트
- `DELETE /api/fitness/{userId}` - 피트니스 데이터 삭제
- `DELETE /api/fitness/{userId}/workouts/{workoutId}` - 운동 기록 삭제

### FSR 데이터
- `POST /api/fsr_data` - FSR 데이터 수신
- `GET /api/fsr_data/latest` - 최신 FSR 데이터 조회
- `GET /api/fsr_data/feedback` - 자세 피드백 조회
- `GET /api/fsr_data/feedback/combined` - 종합 피드백 조회

### AI 추론 세션
- `POST /api/session` - 프론트에서 발급받은 FastAPI 세션을 백엔드에 등록
  - 요청 본문: `{ "userId": "게스트ID", "fastApiSessionId": "FastAPI에서 발급받은 세션ID" }`
  - 응답: `{ "sessionId": "Spring 세션 ID", "fastApiUrl": null, "fastApiSessionId": "FastAPI 세션 ID" }`
- `POST /api/session/{sessionId}/finish` - 세션 완료 (Spring 세션 ID 사용)

### 세션 업그레이드
- `POST /auth/upgrade` - 세션 업그레이드 (게스트 ID 기반)

## 게스트 모드 특징

1. **인증 불필요**: 모든 API가 공개적으로 접근 가능합니다.
2. **게스트 ID**: 각 클라이언트는 UUID 기반 게스트 ID를 사용합니다.
3. **세션 관리**: 게스트 세션은 서버에서 자동으로 생성됩니다.
4. **데이터 격리**: 게스트 ID를 기반으로 데이터를 구분합니다.
5. **FastAPI 통신**: 프론트엔드가 FastAPI와 직접 통신하며, 백엔드는 세션 정보만 저장합니다.

## 사용 예시

### 게스트 세션 생성

```bash
curl -X POST http://localhost:8080/api/guest/session
```

응답:
```json
{
  "guestId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "게스트 세션이 생성되었습니다."
}
```

### FastAPI 세션 등록

프론트엔드가 FastAPI에서 세션을 발급받은 후, 백엔드에 등록:

```bash
curl -X POST http://localhost:8080/api/session \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "fastApiSessionId": "session_7f83a1f3"
  }'
```

응답:
```json
{
  "sessionId": "spring-session-uuid",
  "fastApiUrl": null,
  "fastApiSessionId": "session_7f83a1f3"
}
```

### 피트니스 데이터 저장

```bash
curl -X POST http://localhost:8080/api/fitness/save \
  -H "Content-Type: application/json" \
  -d '{
    "userID": "550e8400-e29b-41d4-a716-446655440000",
    "totalWorkouts": 10,
    "workoutRecordList": [...]
  }'
```

## 주의사항

1. **데이터 영구성**: 현재는 인메모리 저장소를 사용하므로 서버 재시작 시 데이터가 손실됩니다.
2. **보안**: 게스트 모드이므로 모든 데이터가 공개적으로 접근 가능합니다.
3. **프로덕션 환경**: 실제 서비스에서는 적절한 인증 및 권한 관리가 필요합니다.

## 문제 해결

### 일반적인 오류
- 데이터베이스 연결 실패: `application.yml`의 데이터베이스 설정 확인
- 포트 충돌: `server.port` 설정 변경

### 로그 확인
```bash
# 애플리케이션 로그 확인
tail -f logs/application.log
```

## 기술 스택

- Spring Boot 3.4.4
- Java 17
- MySQL 8.0
- WebSocket
- JWT (FastAPI 통신용)
