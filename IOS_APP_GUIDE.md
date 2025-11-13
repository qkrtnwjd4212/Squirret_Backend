# Squirret iOS 앱 개발 가이드

## 목차
1. [개요](#개요)
2. [기본 설정](#기본-설정)
3. [주요 동작 흐름](#주요-동작-흐름)
4. [API 사용 가이드](#api-사용-가이드)
5. [WebSocket 연결](#websocket-연결)
6. [Swift 구현 예제](#swift-구현-예제)

---

## 개요

Squirret 백엔드는 **게스트 모드**로 운영되며, 별도의 로그인 없이 모든 API를 사용할 수 있습니다.

### 서버 정보
- **Base URL**: `http://54.86.161.187:8080`
- **STOMP WebSocket**: `ws://54.86.161.187:8080/ws`
- **Pure WebSocket (FSR)**: `ws://54.86.161.187:8080/ws/fsr-data`
- **Content-Type**: `application/json; charset=utf-8`

### 주요 특징
- 인증 불필요: 모든 REST API는 공개적으로 접근 가능
- 자동 게스트 ID 발급: STOMP 연결 시 서버가 자동으로 게스트 ID 생성
- 실시간 피드백: STOMP WebSocket을 통한 1초 주기 측정값 및 10초 주기 음성 피드백

---

## 기본 설정

### 1. 필요한 라이브러리

#### Swift Package Manager
```swift
// Package.swift 또는 Xcode에서 패키지 추가
dependencies: [
    .package(url: "https://github.com/facebook/facebook-swift-sdk", from: "16.0.0"), // STOMP 클라이언트
    // 또는 Starscream for WebSocket: .package(url: "https://github.com/daltoniam/Starscream.git", from: "4.0.0")
]
```

### 2. 네트워크 설정

#### Info.plist 설정 (HTTPS가 아닌 경우)
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```

---

## 주요 동작 흐름

### 전체 흐름도

```
1. 앱 시작
   ↓
2. 게스트 세션 생성 (POST /api/guest/session)
   ↓
3. STOMP WebSocket 연결 (ws://.../ws)
   ↓
4. /user/queue/session 구독
   ↓
5. FastAPI 세션 발급 및 등록 (POST /api/session)
   ↓
6. 실시간 피드백 수신 (1초/10초 주기)
   ↓
7. FSR 데이터 업로드 (POST /api/fsr_data) - 선택사항
   ↓
8. 세션 완료 (POST /api/session/{sessionId}/finish)
   ↓
9. WebSocket 연결 종료
```

---

## API 사용 가이드

### 1. 게스트 세션 생성

앱 시작 시 게스트 ID를 발급받습니다.

**엔드포인트**: `POST /api/guest/session`

**요청**:
```json
없음 (빈 body 또는 body 없음)
```

**응답**:
```json
{
  "guestId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "게스트 세션이 생성되었습니다."
}
```

**Swift 예제**:
```swift
func createGuestSession() async throws -> String {
    guard let url = URL(string: "http://54.86.161.187:8080/api/guest/session") else {
        throw NSError(domain: "InvalidURL", code: -1)
    }
    
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    
    let (data, _) = try await URLSession.shared.data(for: request)
    let response = try JSONDecoder().decode(GuestSessionResponse.self, from: data)
    
    return response.guestId
}

struct GuestSessionResponse: Codable {
    let guestId: String
    let message: String
}
```

---

### 2. FastAPI 세션 등록

FastAPI에서 발급받은 세션 ID를 백엔드에 등록합니다.

**엔드포인트**: `POST /api/session`

**요청**:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",  // 게스트 ID (선택사항)
  "fastApiSessionId": "session_7f83a1f3"  // FastAPI에서 발급받은 세션 ID (필수)
}
```

**응답**:
```json
{
  "sessionId": "123e4567-e89b-12d3-a456-426614174000",  // Spring 세션 ID
  "fastApiUrl": null,
  "fastApiSessionId": "session_7f83a1f3"
}
```

**Swift 예제**:
```swift
func registerFastApiSession(guestId: String, fastApiSessionId: String) async throws -> InferenceSessionResponse {
    guard let url = URL(string: "http://54.86.161.187:8080/api/session") else {
        throw NSError(domain: "InvalidURL", code: -1)
    }
    
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    
    let body = RegisterSessionRequest(
        userId: guestId,
        fastApiSessionId: fastApiSessionId
    )
    request.httpBody = try JSONEncoder().encode(body)
    
    let (data, _) = try await URLSession.shared.data(for: request)
    return try JSONDecoder().decode(InferenceSessionResponse.self, from: data)
}

struct RegisterSessionRequest: Codable {
    let userId: String?
    let fastApiSessionId: String
}

struct InferenceSessionResponse: Codable {
    let sessionId: String
    let fastApiUrl: String?
    let fastApiSessionId: String
}
```

---

### 3. FSR 데이터 업로드 (선택사항)

ESP32 또는 외부 디바이스에서 FSR 데이터를 업로드할 때 사용합니다.

**엔드포인트**: `POST /api/fsr_data`

**요청**:
```json
{
  "side": "left",  // "left" 또는 "right"
  "ratio1": 45.2,
  "ratio2": 30.1,
  "ratio3": 15.5,
  "ratio4": 25.3,
  "ratio5": 20.0,
  "ratio6": 10.0
}
```

**응답**: `202 Accepted`
```json
{
  "status": "ACCEPTED"
}
```

**Swift 예제**:
```swift
func uploadFSRData(side: String, ratios: [Float]) async throws {
    guard let url = URL(string: "http://54.86.161.187:8080/api/fsr_data") else {
        throw NSError(domain: "InvalidURL", code: -1)
    }
    
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    
    let body = FSRDataRequest(
        side: side,
        ratio1: ratios[0],
        ratio2: ratios[1],
        ratio3: ratios[2],
        ratio4: ratios[3],
        ratio5: ratios[4],
        ratio6: ratios[5]
    )
    request.httpBody = try JSONEncoder().encode(body)
    
    let (_, response) = try await URLSession.shared.data(for: request)
    // 응답 확인 생략
}

struct FSRDataRequest: Codable {
    let side: String
    let ratio1: Float
    let ratio2: Float
    let ratio3: Float
    let ratio4: Float
    let ratio5: Float
    let ratio6: Float
}
```

---

### 4. 최신 FSR 데이터 조회

**엔드포인트**: `GET /api/fsr_data/latest`

**응답**:
```json
{
  "left": {
    "side": "left",
    "ratio1": 45.2,
    "ratio2": 30.1,
    "ratio3": 15.5,
    "ratio4": 25.3,
    "ratio5": 20.0,
    "ratio6": 10.0
  },
  "right": {
    "side": "right",
    "ratio1": 44.8,
    "ratio2": 29.9,
    "ratio3": 15.2,
    "ratio4": 25.1,
    "ratio5": 19.8,
    "ratio6": 9.9
  }
}
```

**Swift 예제**:
```swift
func getLatestFSRData() async throws -> FSRLatestResponse {
    guard let url = URL(string: "http://54.86.161.187:8080/api/fsr_data/latest") else {
        throw NSError(domain: "InvalidURL", code: -1)
    }
    
    let (data, _) = try await URLSession.shared.data(from: url)
    return try JSONDecoder().decode(FSRLatestResponse.self, from: data)
}

struct FSRLatestResponse: Codable {
    let left: FSRFoot?
    let right: FSRFoot?
}

struct FSRFoot: Codable {
    let side: String
    let ratio1: Float
    let ratio2: Float
    let ratio3: Float
    let ratio4: Float
    let ratio5: Float
    let ratio6: Float
}
```

---

### 5. 통합 피드백 조회

AI와 FSR 데이터를 통합한 피드백을 조회합니다.

**엔드포인트**: `GET /api/fsr_data/feedback/combined`

**응답**:
```json
{
  "ai": {
    "status": "GOOD",
    "raw": {
      "lumbar": "good",
      "knee": "bad",
      "ankle": "good"
    },
    "messages": ["무릎 정렬을 유지하세요"]
  },
  "fsr": {
    "stage": "DESCENT",
    "status": "BAD",
    "feedback": "뒤꿈치로 체중을 이동하세요",
    "metrics": {
      "front": 45.5,
      "rear": 54.5,
      "inner": 50.0,
      "outer": 50.0,
      "heel": 55.0,
      "innerOuterDiff": 5.0,
      "leftRightDiff": 10.0
    }
  },
  "overallMessages": ["무릎 정렬을 유지하세요", "뒤꿈치로 체중을 이동하세요"]
}
```

---

### 6. 세션 완료

운동 세션 종료 시 통계를 저장합니다.

**엔드포인트**: `POST /api/session/{sessionId}/finish`

**요청** (선택사항):
```json
{
  "framesIn": 100,
  "framesOut": 95,
  "durationSeconds": 120
}
```

**응답**:
```json
{
  "status": "completed",
  "sessionId": "123e4567-e89b-12d3-a456-426614174000"
}
```

**Swift 예제**:
```swift
func finishSession(sessionId: String, framesIn: Int?, framesOut: Int?, durationSeconds: Int?) async throws {
    guard let url = URL(string: "http://54.86.161.187:8080/api/session/\(sessionId)/finish") else {
        throw NSError(domain: "InvalidURL", code: -1)
    }
    
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    
    let body = SessionFinishRequest(
        framesIn: framesIn,
        framesOut: framesOut,
        durationSeconds: durationSeconds
    )
    request.httpBody = try JSONEncoder().encode(body)
    
    let (_, _) = try await URLSession.shared.data(for: request)
}

struct SessionFinishRequest: Codable {
    let framesIn: Int?
    let framesOut: Int?
    let durationSeconds: Int?
}
```

---

## WebSocket 연결

### STOMP WebSocket 연결

STOMP 프로토콜을 사용하여 실시간 피드백을 수신합니다.

**연결 URL**: `ws://54.86.161.187:8080/ws`
**구독 경로**: `/user/queue/session`

### 메시지 타입

#### 1. DATA 메시지 (1초 주기)
```json
{
  "type": "DATA",
  "payload": {
    "timestamp": 1234567890000,
    "state": "STAND",
    "squatCount": 5,
    "checks": {
      "back": "good",
      "knee": "bad",
      "ankle": "good"
    }
  },
  "ts": 1234567890000
}
```

#### 2. voice 메시지 (10초 주기)
```json
{
  "type": "voice",
  "text": "무릎 정렬을 유지하세요",
  "ts": 1234567890000
}
```

#### 3. feedback 메시지
```json
{
  "type": "feedback",
  "text": "좋은 자세입니다",
  "ts": 1234567890000
}
```

---

## Swift 구현 예제

### 완전한 구현 예제

```swift
import Foundation
import Combine

class SquirretAPIManager: ObservableObject {
    static let shared = SquirretAPIManager()
    
    private let baseURL = "http://54.86.161.187:8080"
    private var guestId: String?
    private var springSessionId: String?
    private var stompClient: StompClient? // STOMP 클라이언트 라이브러리 사용
    
    @Published var currentFeedback: String?
    @Published var squatCount: Int = 0
    @Published var currentState: String? // "SIT" or "STAND"
    
    private init() {}
    
    // MARK: - 세션 관리
    
    /// 1. 게스트 세션 생성
    func createGuestSession() async throws -> String {
        guard let url = URL(string: "\(baseURL)/api/guest/session") else {
            throw NSError(domain: "InvalidURL", code: -1)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let (data, _) = try await URLSession.shared.data(for: request)
        let response = try JSONDecoder().decode(GuestSessionResponse.self, from: data)
        
        self.guestId = response.guestId
        return response.guestId
    }
    
    /// 2. FastAPI 세션 등록
    func registerFastApiSession(fastApiSessionId: String) async throws -> String {
        guard let url = URL(string: "\(baseURL)/api/session") else {
            throw NSError(domain: "InvalidURL", code: -1)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body = RegisterSessionRequest(
            userId: guestId,
            fastApiSessionId: fastApiSessionId
        )
        request.httpBody = try JSONEncoder().encode(body)
        
        let (data, _) = try await URLSession.shared.data(for: request)
        let response = try JSONDecoder().decode(InferenceSessionResponse.self, from: data)
        
        self.springSessionId = response.sessionId
        return response.sessionId
    }
    
    // MARK: - WebSocket 연결
    
    /// 3. STOMP WebSocket 연결 및 구독
    func connectWebSocket() {
        let wsURL = "ws://54.86.161.187:8080/ws"
        
        // STOMP 클라이언트 초기화 (라이브러리에 따라 다름)
        stompClient = StompClient(url: wsURL)
        stompClient?.connect()
        
        // 구독 경로 설정
        stompClient?.subscribe(to: "/user/queue/session") { [weak self] message in
            self?.handleWebSocketMessage(message)
        }
    }
    
    /// WebSocket 메시지 처리
    private func handleWebSocketMessage(_ message: Data) {
        guard let json = try? JSONSerialization.jsonObject(with: message) as? [String: Any],
              let type = json["type"] as? String else {
            return
        }
        
        switch type {
        case "DATA":
            if let payload = json["payload"] as? [String: Any] {
                DispatchQueue.main.async {
                    self.squatCount = payload["squatCount"] as? Int ?? 0
                    self.currentState = payload["state"] as? String
                }
            }
            
        case "voice":
            if let text = json["text"] as? String {
                DispatchQueue.main.async {
                    self.currentFeedback = text
                    // 음성 안내 또는 UI 업데이트
                }
            }
            
        case "feedback":
            if let text = json["text"] as? String {
                DispatchQueue.main.async {
                    self.currentFeedback = text
                }
            }
            
        default:
            break
        }
    }
    
    /// WebSocket 연결 종료
    func disconnectWebSocket() {
        stompClient?.disconnect()
    }
    
    // MARK: - 세션 완료
    
    /// 4. 세션 완료
    func finishSession(framesIn: Int?, framesOut: Int?, durationSeconds: Int?) async throws {
        guard let sessionId = springSessionId,
              let url = URL(string: "\(baseURL)/api/session/\(sessionId)/finish") else {
            throw NSError(domain: "NoSessionId", code: -1)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body = SessionFinishRequest(
            framesIn: framesIn,
            framesOut: framesOut,
            durationSeconds: durationSeconds
        )
        request.httpBody = try JSONEncoder().encode(body)
        
        let (_, _) = try await URLSession.shared.data(for: request)
    }
}

// MARK: - 데이터 모델

struct GuestSessionResponse: Codable {
    let guestId: String
    let message: String
}

struct RegisterSessionRequest: Codable {
    let userId: String?
    let fastApiSessionId: String
}

struct InferenceSessionResponse: Codable {
    let sessionId: String
    let fastApiUrl: String?
    let fastApiSessionId: String
}

struct SessionFinishRequest: Codable {
    let framesIn: Int?
    let framesOut: Int?
    let durationSeconds: Int?
}

// MARK: - 사용 예제

/*
// 앱 시작 시
Task {
    do {
        // 1. 게스트 세션 생성
        let guestId = try await SquirretAPIManager.shared.createGuestSession()
        print("게스트 ID: \(guestId)")
        
        // 2. WebSocket 연결
        SquirretAPIManager.shared.connectWebSocket()
        
        // 3. FastAPI 세션 발급 후 등록
        let fastApiSessionId = "session_xxx" // FastAPI에서 받은 세션 ID
        let springSessionId = try await SquirretAPIManager.shared.registerFastApiSession(
            fastApiSessionId: fastApiSessionId
        )
        print("Spring 세션 ID: \(springSessionId)")
        
    } catch {
        print("에러: \(error)")
    }
}

// 세션 종료 시
Task {
    do {
        try await SquirretAPIManager.shared.finishSession(
            framesIn: 100,
            framesOut: 95,
            durationSeconds: 120
        )
        SquirretAPIManager.shared.disconnectWebSocket()
    } catch {
        print("에러: \(error)")
    }
}
*/
```

---

## 주의사항

1. **게스트 ID 사용**
   - `POST /api/guest/session`으로 받은 guestId는 `POST /api/session` 요청의 `userId` 필드에 사용합니다.
   - STOMP 연결 시 서버가 자동으로 게스트 ID를 생성하지만, REST API 호출 시에는 명시적으로 guestId를 전달해야 합니다.

2. **WebSocket 연결 유지**
   - 피드백을 수신하려면 STOMP WebSocket 연결이 유지되어야 합니다.
   - 앱이 백그라운드로 전환되거나 종료될 때 연결을 종료하는 것을 권장합니다.

3. **에러 처리**
   - 모든 네트워크 호출에 대해 적절한 에러 처리를 구현하세요.
   - WebSocket 연결 실패 시 재연결 로직을 구현하는 것을 권장합니다.

4. **스레드 안전성**
   - UI 업데이트는 반드시 메인 스레드에서 수행하세요.

---

## 참고 자료

- [OpenAPI 명세](http://54.86.161.187:8080/swagger-ui.html)
- 테스트 가이드: `TESTING.md`

