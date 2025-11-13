# Squirret iOS 앱 개발 가이드 - 요약

## 🚀 빠른 시작

### 서버 정보
- **Base URL**: `http://54.86.161.187:8080`
- **STOMP WebSocket**: `ws://54.86.161.187:8080/ws`
- **게스트 모드**: 인증 불필요, 모든 API 공개 접근 가능

---

## 📋 필수 작업 순서

```
1. 게스트 세션 생성 → 2. STOMP 연결 → 3. FastAPI 세션 등록 → 4. 피드백 수신 → 5. 세션 완료
```

### 1️⃣ 게스트 세션 생성
```swift
POST /api/guest/session
→ 응답: { "guestId": "...", "message": "..." }
```

### 2️⃣ STOMP WebSocket 연결
```
연결: ws://54.86.161.187:8080/ws
구독: /user/queue/session
```

### 3️⃣ FastAPI 세션 등록
```swift
POST /api/session
Body: { "userId": "게스트ID", "fastApiSessionId": "FastAPI세션ID" }
→ 응답: { "sessionId": "Spring세션ID", ... }
```

### 4️⃣ 실시간 피드백 수신
- **DATA 메시지** (1초 주기): `{"type":"DATA","payload":{...}}`
- **voice 메시지** (10초 주기): `{"type":"voice","text":"..."}`

### 5️⃣ 세션 완료
```swift
POST /api/session/{sessionId}/finish
Body: { "framesIn": 100, "framesOut": 95, "durationSeconds": 120 }
```

---

## 🔑 핵심 API 목록

| 엔드포인트 | 메서드 | 용도 |
|-----------|--------|------|
| `/api/guest/session` | POST | 게스트 ID 발급 |
| `/api/session` | POST | FastAPI 세션 등록 |
| `/api/session/{sessionId}/finish` | POST | 세션 완료 통계 저장 |
| `/api/fsr_data/latest` | GET | 최신 FSR 데이터 조회 |
| `/api/fsr_data/feedback/combined` | GET | AI + FSR 통합 피드백 |

---

## 💡 핵심 개념

### 게스트 ID (guestId)
- **용도**: FastAPI 세션 등록 시 `userId`로 사용
- **발급**: `POST /api/guest/session` 호출
- **참고**: STOMP 연결 시 서버가 자동 생성하는 ID와 별개

### Spring 세션 ID (sessionId)
- **용도**: 세션 완료 및 관리
- **발급**: `POST /api/session` 호출 시 반환
- **필요 시**: `POST /api/session/{sessionId}/finish`에서 사용

### WebSocket 메시지 타입
1. **DATA**: 1초 주기 측정값 (squatCount, state, checks 등)
2. **voice**: 10초 주기 음성 피드백 텍스트
3. **feedback**: 일반 피드백 메시지

---

## 📝 최소 구현 코드

```swift
class SquirretManager {
    static let shared = SquirretManager()
    private let baseURL = "http://54.86.161.187:8080"
    private var guestId: String?
    private var springSessionId: String?
    
    // 1. 게스트 세션 생성
    func createGuestSession() async throws -> String {
        let url = URL(string: "\(baseURL)/api/guest/session")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        let (data, _) = try await URLSession.shared.data(for: request)
        let response = try JSONDecoder().decode(GuestSessionResponse.self, from: data)
        self.guestId = response.guestId
        return response.guestId
    }
    
    // 2. FastAPI 세션 등록
    func registerSession(fastApiSessionId: String) async throws -> String {
        let url = URL(string: "\(baseURL)/api/session")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        let body = ["userId": guestId, "fastApiSessionId": fastApiSessionId]
        request.httpBody = try JSONEncoder().encode(body)
        let (data, _) = try await URLSession.shared.data(for: request)
        let response = try JSONDecoder().decode(InferenceSessionResponse.self, from: data)
        self.springSessionId = response.sessionId
        return response.sessionId
    }
    
    // 3. STOMP 연결 (라이브러리 사용)
    func connectWebSocket() {
        // STOMP 클라이언트로 ws://54.86.161.187:8080/ws 연결
        // /user/queue/session 구독
    }
    
    // 4. 세션 완료
    func finishSession() async throws {
        guard let sessionId = springSessionId else { return }
        let url = URL(string: "\(baseURL)/api/session/\(sessionId)/finish")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        let (_, _) = try await URLSession.shared.data(for: request)
    }
}

struct GuestSessionResponse: Codable {
    let guestId: String
    let message: String
}

struct InferenceSessionResponse: Codable {
    let sessionId: String
    let fastApiSessionId: String
}
```

---

## ⚠️ 주의사항

1. **게스트 ID 사용**: REST API 호출 시 명시적으로 전달 필요
2. **WebSocket 유지**: 피드백 수신을 위해 연결 유지 필수
3. **메인 스레드**: UI 업데이트는 반드시 메인 스레드에서
4. **에러 처리**: 네트워크 실패 시 재연결 로직 구현 권장

---

## 🔗 참고

- 상세 가이드: `IOS_APP_GUIDE.md`
- OpenAPI 명세: http://54.86.161.187:8080/swagger-ui.html
- 테스트 가이드: `TESTING.md`

