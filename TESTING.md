
# Squirret iOS 연동 — 최소 클릭 가이드 & API 명세 (단일 문서)

## 0) 공통

* **Base URL**: `http://54.86.161.187:8080`
* **WS Base**: `ws://54.86.161.187:8080`
* **Content-Type**: `application/json; charset=utf-8`
* **인증**: WebSocket 접속 시 `?token=<wsToken>` (게스트/유저 공통). REST는 현재 공개(향후 Bearer 예정).
* **시간**: Unix epoch sec 또는 ISO-8601
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
4. **로그인 후 승격**(guest→user) → 새 `wsToken`으로 재연결
5. **FSR(깔창) 데이터**: REST(스냅샷/피드백) + WS(실시간) 병행

---

## 2) iOS 프로젝트 세팅

* **SPM**: `https://github.com/WrathChaos/StompClientLib.git`
* 권장 환경: iOS 15+, 개발 단계는 ATS 예외(HTTP/WS) 또는 운영은 HTTPS/WSS 사용

```swift
enum API {
    static let host = "54.86.161.187"
    static let base = "http://\(host):8080"
    static let ws   = "ws://\(host):8080"
}
```

---

## 3) 세션/인증 API

### 3.1 게스트 세션 발급

* **POST** `/internal/session` (바디 없음)
* **응답 200**

```json
{ "sessionId": "e0e1c6af-...", "wsToken": "eyJhbGciOi..." }
```

**Swift**

```swift
struct SessionIssueResp: Decodable { let sessionId: String; let wsToken: String }

func issueSession() async throws -> SessionIssueResp {
    let url = URL(string: "\(API.base)/internal/session")!
    var req = URLRequest(url: url)
    req.httpMethod = "POST"
    req.addValue("application/json", forHTTPHeaderField: "Content-Type")
    let (data, _) = try await URLSession.shared.data(for: req)
    return try JSONDecoder().decode(SessionIssueResp.self, from: data)
}
```

### 3.2 승격(guest→user)

* **POST** `/auth/upgrade`
* **요청**

```json
{ "userId":"<UUID>", "sessionId":"<세션ID>", "email":"user@example.com" }
```

* **응답 200**

```json
{ "sessionId":"e0e1c6af-...", "wsToken":"NEW_USER_TOKEN_JWT" }
```

**Swift**

```swift
struct UpgradeResp: Decodable { let sessionId: String; let wsToken: String }

func upgradeSession(userId: String, sessionId: String, email: String) async throws -> UpgradeResp {
    let url = URL(string: "\(API.base)/auth/upgrade")!
    var req = URLRequest(url: url)
    req.httpMethod = "POST"
    req.addValue("application/json", forHTTPHeaderField: "Content-Type")
    let body = ["userId": userId, "sessionId": sessionId, "email": email]
    req.httpBody = try JSONSerialization.data(withJSONObject: body)
    let (data, _) = try await URLSession.shared.data(for: req)
    return try JSONDecoder().decode(UpgradeResp.self, from: data)
}
```

---

## 4) WebSocket/STOMP

### 4.1 연결 정보

* **URL**: `ws://54.86.161.187:8080/ws?token=<wsToken>`
* **프로토콜**: 순수 WebSocket + STOMP 1.2
* **필수**: 모든 STOMP 프레임 끝에 널 문자 `\u0000`
* **구독**: `/user/queue/session`
* **송신**: `/app/session.message` (JSON)

### 4.2 iOS STOMP 예제

```swift
import StompClientLib

final class WSService: NSObject, StompClientLibDelegate {
    private let stomp = StompClientLib()
    private var request: NSURLRequest!

    func connect(wsToken: String) {
        let url = NSURL(string: "\(API.ws)/ws?token=\(wsToken)")!
        request = NSURLRequest(url: url as URL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        stomp.openSocketWithURLRequest(request: request, delegate: self)
    }

    func stompClientDidConnect(client: StompClientLib!) {
        client.subscribe(destination: "/user/queue/session")
        let ping: [String: Any] = ["type":"PING", "payload":["ts": Date().timeIntervalSince1970]]
        client.sendJSONForDict(dict: ping as NSDictionary, toDestination: "/app/session.message")
    }

    func stompClient(_ client: StompClientLib!, didReceiveMessageWithJSONBody jsonBody: AnyObject?, akaStringBody stringBody: String?, withHeader header: [String : String]?, withDestination destination: String) {
        // 서버 push: DATA(1초), voice/FEEDBACK(10초)
        print("recv \(destination): \(stringBody ?? "")")
    }

    func serverDidSendError(client: StompClientLib!, withErrorMessage description: String, detailedErrorMessage: String?) { reconnect() }
    func stompClientDidDisconnect(client: StompClientLib!) { reconnect() }
    private func reconnect(delay: TimeInterval = 1) {
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self else { return }
            self.stomp.openSocketWithURLRequest(request: self.request, delegate: self)
        }
    }

    func sendClientEvent(name: String) {
        let cmd: [String: Any] = ["type":"CLIENT_EVENT", "payload":["name": name, "ts": Date().timeIntervalSince1970]]
        stomp.sendJSONForDict(dict: cmd as NSDictionary, toDestination: "/app/session.message")
    }
}

// 최초 실행 예
let ws = WSService()
Task {
    let s = try await issueSession()
    ws.connect(wsToken: s.wsToken)
}
```

### 4.3 메시지 규격(서버→앱 예시)

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

**Swift**

```swift
struct FSRFoot: Decodable {
    let side: String
    let ratio1, ratio2, ratio3, ratio4, ratio5, ratio6: Double
}
struct FSRLatest: Decodable { let left: FSRFoot?; let right: FSRFoot? }

func fetchFSRLatest() async throws -> FSRLatest {
    let (data, _) = try await URLSession.shared.data(from: URL(string: "\(API.base)/api/fsr_data/latest")!)
    return try JSONDecoder().decode(FSRLatest.self, from: data)
}
```

### 5.3 실시간 스트리밍(WS 브로드캐스트)

* **URL**: `ws://54.86.161.187:8080/ws/fsr-data` (STOMP 아님, **순수 WebSocket JSON**)
* **동작**: 연결 즉시 최신 스냅샷 1회 → 새 데이터마다 push

```swift
final class FSRStream {
    private var task: URLSessionWebSocketTask?
    func connect() {
        let url = URL(string: "\(API.ws)/ws/fsr-data")!
        task = URLSession.shared.webSocketTask(with: url)
        task?.resume()
        receive()
    }
    private func receive() {
        task?.receive { [weak self] result in
            if case .success(.string(let text)) = result { print("FSR WS:", text) } // JSON 파싱 → UI 반영
            self?.receive()
        }
    }
    func disconnect() { task?.cancel(with: .goingAway, reason: nil) }
}
```

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

**Swift**

```swift
struct FSRFeedback: Decodable {
    let stage: String?      // "DESCENT", "ASCENT", "UNKNOWN"
    let status: String?     // "GOOD", "BAD", "NO_DATA"
    let feedback: String?
    let metrics: [String: Double]?
}

func fetchFSRFeedback() async throws -> FSRFeedback {
    let (data, _) = try await URLSession.shared.data(from: URL(string: "\(API.base)/api/fsr_data/feedback")!)
    return try JSONDecoder().decode(FSRFeedback.self, from: data)
}
```

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

**Swift**

```swift
struct CombinedFeedback: Decodable {
    struct AI: Decodable {
        let status: String
        let raw: [String: String]?
        let messages: [String]?
    }
    struct FSR: Decodable {
        let stage: String?
        let status: String?
        let feedback: String?
        let metrics: [String: Double]?
    }
    let ai: AI
    let fsr: FSR
    let overallMessages: [String]
}

func fetchCombinedFeedback() async throws -> CombinedFeedback {
    let (data, _) = try await URLSession.shared.data(from: URL(string: "\(API.base)/api/fsr_data/feedback/combined")!)
    return try JSONDecoder().decode(CombinedFeedback.self, from: data)
}
```

---

## 6) 뷰모델 배선 샘플

```swift
@MainActor
final class PoseViewModel: ObservableObject {
    @Published var latest: FSRLatest?
    @Published var fsrFeedback: FSRFeedback?
    @Published var combinedFeedback: CombinedFeedback?
    @Published var lastMessage: String = ""
    private let ws = WSService()
    private let fsrWS = FSRStream()
    private var session: SessionIssueResp?

    func start() async {
        do {
            let s = try await issueSession()
            session = s
            ws.connect(wsToken: s.wsToken)
            fsrWS.connect()
            latest = try await fetchFSRLatest()
        } catch {
            lastMessage = "시작 실패: \(error.localizedDescription)"
        }
    }

    // FSR 피드백만 요청
    func requestFSRFeedback() async {
        do {
            fsrFeedback = try await fetchFSRFeedback()
            lastMessage = fsrFeedback?.feedback ?? "피드백 없음"
        } catch {
            lastMessage = "FSR 피드백 실패: \(error.localizedDescription)"
        }
    }

    // AI + FSR 통합 피드백 요청
    func requestCombinedFeedback() async {
        do {
            combinedFeedback = try await fetchCombinedFeedback()
            lastMessage = combinedFeedback?.overallMessages.joined(separator: "\n") ?? "피드백 없음"
        } catch {
            lastMessage = "통합 피드백 실패: \(error.localizedDescription)"
        }
    }
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

# 4) 세션 승격 (로그인 후)
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<UUID>","sessionId":"<세션ID>","email":"user@example.com"}' \
  http://54.86.161.187:8080/auth/upgrade | jq

# 5) AI 상태 입력(내부)
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"lumbar":"good","knee":"bad","ankle":"null"}' \
  http://54.86.161.187:8080/internal/ai/status | jq

# 6) FastAPI 웹소켓 세션 발급
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"user123"}' \
  http://54.86.161.187:8080/api/session | jq

# 7) 토큰 갱신
curl -s -X POST -H "Content-Type: application/json" \
  http://54.86.161.187:8080/api/session/{sessionId}/refresh | jq

# 8) 세션 완료
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"framesIn":150,"framesOut":150,"durationSeconds":30}' \
  http://54.86.161.187:8080/api/session/{sessionId}/finish | jq
```

---

## 8) FastAPI 웹소켓 연결 가이드 (하이브리드 구조)

### 8.1 아키텍처 개요

* **Spring (컨트롤 플레인)**: 세션/토큰 발급, 레이트리밋, 로깅, 과금
  - **역할**: 세션 생성, 토큰 발급/갱신, 세션 완료 처리만 담당
  - **웹소켓 프록시 없음**: Spring은 웹소켓 연결을 중계하지 않음
  
* **FastAPI (데이터 플레인)**: 웹소켓으로 직접 연결, 영상 분석 처리
  - **역할**: 앱과 직접 웹소켓 연결, 영상 프레임 수신 및 분석, 결과 전송
  
* **iOS 앱**: Spring에서 세션 받아서 FastAPI WebSocket에 **직접** 연결
  - **1단계**: Spring API 호출 → `wsUrl`, `wsToken` 수신
  - **2단계**: 받은 `wsUrl?token=wsToken`으로 FastAPI에 **직접** 연결
  - **3단계**: FastAPI와 직접 통신 (Spring 경유 없음)

### 8.1.1 연결 흐름도

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│ iOS 앱  │                    │ Spring  │                    │ FastAPI │
└────┬────┘                    └────┬────┘                    └────┬────┘
     │                              │                              │
     │  1. POST /api/session        │                              │
     │─────────────────────────────>│                              │
     │                              │                              │
     │  2. {sessionId, wsUrl, wsToken}                              │
     │<─────────────────────────────│                              │
     │                              │                              │
     │  3. wsUrl?token=wsToken 연결  │                              │
     │────────────────────────────────────────────────────────────>│
     │                              │                              │
     │  4. 영상 프레임 전송          │                              │
     │────────────────────────────────────────────────────────────>│
     │                              │                              │
     │  5. 분석 결과 수신            │                              │
     │<────────────────────────────────────────────────────────────│
     │                              │                              │
     │  6. POST /api/session/{id}/refresh (토큰 갱신 필요 시)        │
     │─────────────────────────────>│                              │
     │                              │                              │
     │  7. POST /api/session/{id}/finish (세션 종료 시)            │
     │─────────────────────────────>│                              │
```

**핵심 포인트**:
- ✅ Spring은 **세션 발급만** 담당 (웹소켓 연결 중계 안 함)
- ✅ 앱 → FastAPI는 **직접 연결** (Spring 경유 없음)
- ✅ Spring API는 세션 관리용으로만 사용 (토큰 갱신, 세션 완료)

### 8.2 Spring 세션 발급 API

#### 8.2.1 세션 생성

* **POST** `/api/session`
* **요청**:
```json
{ "userId": "user123" }
```

* **응답 200**:
```json
{
  "sessionId": "e0e1c6af-...",
  "wsUrl": "ws://inference.your.com/ws/e0e1c6af-...",
  "wsToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Swift**
```swift
struct InferenceSession: Decodable {
    let sessionId: String
    let wsUrl: String
    let wsToken: String
}

func createInferenceSession(userId: String) async throws -> InferenceSession {
    let url = URL(string: "\(API.base)/api/session")!
    var req = URLRequest(url: url)
    req.httpMethod = "POST"
    req.addValue("application/json", forHTTPHeaderField: "Content-Type")
    req.httpBody = try JSONSerialization.data(withJSONObject: ["userId": userId])
    let (data, _) = try await URLSession.shared.data(for: req)
    return try JSONDecoder().decode(InferenceSession.self, from: data)
}
```

#### 8.2.2 토큰 갱신

* **POST** `/api/session/{sessionId}/refresh`
* **응답 200**:
```json
{
  "sessionId": "e0e1c6af-...",
  "wsToken": "NEW_TOKEN_JWT"
}
```

**Swift**
```swift
struct RefreshTokenResponse: Decodable {
    let sessionId: String
    let wsToken: String
}

func refreshInferenceToken(sessionId: String) async throws -> RefreshTokenResponse {
    let url = URL(string: "\(API.base)/api/session/\(sessionId)/refresh")!
    var req = URLRequest(url: url)
    req.httpMethod = "POST"
    let (data, _) = try await URLSession.shared.data(for: req)
    return try JSONDecoder().decode(RefreshTokenResponse.self, from: data)
}
```

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

### 8.3 FastAPI 웹소켓 연결 (iOS)

#### 8.3.1 연결 방법

**중요**: Spring에서 세션만 발급받고, **앱에서 FastAPI 웹소켓에 직접 연결**합니다.

1. **Spring에서 세션 발급**:
   ```swift
   let session = try await createInferenceSession(userId: "user123")
   // session.wsUrl = "ws://inference.your.com/ws/e0e1c6af-..."
   // session.wsToken = "eyJhbGciOi..."
   ```

2. **FastAPI에 직접 연결** (Spring 경유 없음):
   ```swift
   let url = URL(string: "\(session.wsUrl)?token=\(session.wsToken)")!
   // 이 URL은 FastAPI 서버를 직접 가리킴 (Spring이 아님!)
   ```

* **URL**: `{wsUrl}?token={wsToken}` (Spring에서 받은 값 사용, **FastAPI 서버로 직접 연결**)
* **프로토콜**: 순수 WebSocket (JSON 또는 바이너리 프레임)
* **인증**: 쿼리 파라미터 `token`에 JWT 토큰 전달
* **연결 경로**: `앱 → FastAPI (직접)` (Spring 거치지 않음)

**Swift**
```swift
import Foundation

final class InferenceWebSocket {
    private var task: URLSessionWebSocketTask?
    private var sessionId: String?
    private var wsUrl: String?
    private var wsToken: String?
    private var tokenRefreshTimer: Timer?

    func connect(session: InferenceSession) {
        self.sessionId = session.sessionId
        self.wsUrl = session.wsUrl
        self.wsToken = session.wsToken
        
        guard let wsUrl = wsUrl, let wsToken = wsToken else { return }
        let url = URL(string: "\(wsUrl)?token=\(wsToken)")!
        
        task = URLSession.shared.webSocketTask(with: url)
        task?.resume()
        
        // 토큰 만료 1-2분 전 갱신 타이머 시작 (15분 토큰 기준, 13분 후 갱신)
        startTokenRefreshTimer()
        
        receive()
    }

    private func receive() {
        task?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    print("FastAPI WS 수신: \(text)")
                    // JSON 파싱 후 처리
                case .data(let data):
                    print("FastAPI WS 수신 (바이너리): \(data.count) bytes")
                @unknown default:
                    break
                }
            case .failure(let error):
                print("FastAPI WS 수신 오류: \(error)")
            }
            self.receive()
        }
    }

    func sendFrame(imageBase64: String, frameNumber: Int) {
        let message: [String: Any] = [
            "type": "frame",
            "frameNumber": frameNumber,
            "image_base64": imageBase64,
            "timestamp": Date().timeIntervalSince1970
        ]
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: message),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            return
        }
        
        task?.send(.string(jsonString)) { error in
            if let error = error {
                print("FastAPI WS 전송 오류: \(error)")
            }
        }
    }

    private func startTokenRefreshTimer() {
        // 13분 후 토큰 갱신 (15분 토큰의 87% 시점)
        tokenRefreshTimer = Timer.scheduledTimer(withTimeInterval: 13 * 60, repeats: false) { [weak self] _ in
            self?.refreshTokenAndReconnect()
        }
    }

    private func refreshTokenAndReconnect() {
        guard let sessionId = sessionId else { return }
        
        Task {
            do {
                let refreshed = try await refreshInferenceToken(sessionId: sessionId)
                self.wsToken = refreshed.wsToken
                
                // 무중단 재연결
                disconnect()
                if let wsUrl = wsUrl, let wsToken = wsToken {
                    let url = URL(string: "\(wsUrl)?token=\(wsToken)")!
                    task = URLSession.shared.webSocketTask(with: url)
                    task?.resume()
                    startTokenRefreshTimer()
                    receive()
                }
            } catch {
                print("토큰 갱신 실패: \(error)")
            }
        }
    }

    func disconnect() {
        tokenRefreshTimer?.invalidate()
        task?.cancel(with: .goingAway, reason: nil)
    }

    func finishSession(framesIn: Int, framesOut: Int, durationSeconds: Int) {
        guard let sessionId = sessionId else { return }
        
        Task {
            let url = URL(string: "\(API.base)/api/session/\(sessionId)/finish")!
            var req = URLRequest(url: url)
            req.httpMethod = "POST"
            req.addValue("application/json", forHTTPHeaderField: "Content-Type")
            let body: [String: Any] = [
                "framesIn": framesIn,
                "framesOut": framesOut,
                "durationSeconds": durationSeconds
            ]
            req.httpBody = try? JSONSerialization.data(withJSONObject: body)
            _ = try? await URLSession.shared.data(for: req)
        }
    }
}
```

### 8.4 사용 예시 (iOS)

**전체 흐름 (3단계)**:
1. **Spring API 호출**: 세션 발급 받기 (`POST /api/session`)
2. **FastAPI 직접 연결**: 받은 `wsUrl`과 `wsToken`으로 FastAPI 웹소켓에 직접 연결
3. **FastAPI와 통신**: 영상 프레임 전송, 분석 결과 수신 (Spring 경유 없음)
4. **세션 관리**: 토큰 갱신/세션 완료는 Spring API 호출

```swift
@MainActor
final class InferenceViewModel: ObservableObject {
    @Published var isConnected = false
    @Published var lastResponse: String = ""
    private let inferenceWS = InferenceWebSocket()
    private var frameCounter = 0
    private var sessionStartTime: Date?

    func startInference(userId: String) async {
        do {
            // ═══════════════════════════════════════════════════════
            // 1단계: Spring에서 세션 발급 (세션 정보만 받음)
            // ═══════════════════════════════════════════════════════
            let session = try await createInferenceSession(userId: userId)
            print("✅ 세션 발급 완료: \(session.sessionId)")
            print("✅ FastAPI URL: \(session.wsUrl)")
            print("⚠️  이제 FastAPI에 직접 연결합니다 (Spring 경유 없음)")
            
            // ═══════════════════════════════════════════════════════
            // 2단계: FastAPI에 직접 연결 (Spring 경유 없음!)
            // ═══════════════════════════════════════════════════════
            inferenceWS.connect(session: session)
            sessionStartTime = Date()
            isConnected = true
        } catch {
            lastResponse = "세션 생성 실패: \(error.localizedDescription)"
        }
    }

    func sendFrame(imageData: Data) {
        // ═══════════════════════════════════════════════════════
        // 3단계: FastAPI와 직접 통신 (Spring 경유 없음)
        // ═══════════════════════════════════════════════════════
        let base64 = imageData.base64EncodedString()
        inferenceWS.sendFrame(imageBase64: base64, frameNumber: frameCounter)
        frameCounter += 1
    }

    func stopInference() {
        // ═══════════════════════════════════════════════════════
        // 세션 완료: Spring API 호출 (통계 저장)
        // ═══════════════════════════════════════════════════════
        let duration = Int((Date().timeIntervalSince(sessionStartTime ?? Date())))
        inferenceWS.finishSession(
            framesIn: frameCounter,
            framesOut: frameCounter,
            durationSeconds: duration
        )
        inferenceWS.disconnect()
        isConnected = false
    }
}
```

**핵심 정리**:
- ✅ Spring: 세션 발급만 담당 (`POST /api/session`)
- ✅ FastAPI: 웹소켓 연결 및 영상 분석 (앱과 직접 통신)
- ✅ 앱: Spring에서 세션 받아서 FastAPI에 직접 연결
- ❌ Spring은 웹소켓 프록시 역할 안 함

### 8.5 FastAPI 서버 구현 예시 (Python)

```python
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Query, HTTPException
import jwt
import json
from typing import Optional

app = FastAPI()

# JWT 검증을 위한 공개키 (Spring의 secret과 동일한 값 사용)
JWT_SECRET = "mySecretKey123456789012345678901234567890"

def verify_jwt_token(token: str, session_id: str) -> bool:
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
        # aud 클레임 확인
        if payload.get("aud") != "inference-ws":
            return False
        # sessionId 확인
        if payload.get("sub") != session_id:
            return False
        return True
    except jwt.ExpiredSignatureError:
        return False
    except jwt.InvalidTokenError:
        return False

@app.websocket("/ws/{session_id}")
async def websocket_endpoint(websocket: WebSocket, session_id: str, token: Optional[str] = Query(None)):
    # 토큰 검증
    if not token or not verify_jwt_token(token, session_id):
        await websocket.close(code=1008, reason="인증 실패")
        return
    
    await websocket.accept()
    
    try:
        while True:
            # 클라이언트로부터 프레임 수신
            data = await websocket.receive_text()
            message = json.loads(data)
            
            if message.get("type") == "frame":
                # 영상 분석 처리
                image_base64 = message.get("image_base64")
                frame_number = message.get("frameNumber")
                
                # 분석 로직 (예시)
                analysis_result = {
                    "type": "analysis",
                    "frameNumber": frame_number,
                    "lumbar": "good",
                    "knee": "bad",
                    "ankle": "good",
                    "score": 85
                }
                
                # 분석 결과 전송
                await websocket.send_text(json.dumps(analysis_result))
                
    except WebSocketDisconnect:
        print(f"세션 종료: {session_id}")
```

### 8.6 운영 체크리스트

* **연결 경로 명확화**: 
  - ✅ Spring API: 세션 발급/갱신/완료만 처리 (웹소켓 중계 안 함)
  - ✅ FastAPI: 앱과 직접 웹소켓 연결 (Spring 경유 없음)
  - ❌ Spring은 웹소켓 프록시 역할을 하지 않음
  
* **JWT 키 운영**: Spring은 비공개키로 서명, FastAPI는 동일한 secret으로 검증
* **레이트리밋**: Spring에서 세션별 `frames_in/out`, `duration_s` 집계
* **로깅/관찰성**: 공통 `sessionId`로 Spring API 로그 ↔ FastAPI 로그 상관추적
* **LB/Nginx**: 
  - FastAPI 서버 앞에 LB 구성 시 WebSocket 업그레이드 설정
  - 세션 고정(해시 라우팅)으로 동일 세션은 동일 서버로 라우팅
* **토큰 만료**: 15분 토큰, 13분 후 자동 갱신으로 무중단 재연결
* **네트워크 설정**: 
  - iOS 앱에서 FastAPI 서버 URL에 직접 접근 가능해야 함
  - 방화벽/보안 그룹에서 FastAPI 웹소켓 포트(예: 8000) 오픈 확인

### 8.7 환경 변수 설정

```bash
# Spring application.yml 또는 환경 변수
INFERENCE_WS_BASE_URL=ws://your-fastapi-server:8000
```

**주의사항**:
- `INFERENCE_WS_BASE_URL`은 **FastAPI 서버의 실제 주소**여야 합니다
- iOS 앱이 이 주소에 직접 접근할 수 있어야 합니다
- 개발 환경: `ws://localhost:8000` (시뮬레이터는 localhost 가능)
- 운영 환경: `wss://inference.your-domain.com` (도메인 + HTTPS/WSS 권장)

### 8.8 FAQ

**Q: Spring 서버를 웹소켓 프록시로 사용할 수 있나요?**  
A: **아니요**. 현재 하이브리드 구조에서는 Spring은 세션 발급만 담당하고, 앱은 FastAPI에 직접 연결합니다.

**Q: 세션만 발급하면 웹소켓 연결은 앱에서 알아서 하나요?**  
A: **네, 맞습니다**. 
1. Spring API(`POST /api/session`)로 세션 발급 → `wsUrl`, `wsToken` 받기
2. 받은 `wsUrl?token=wsToken`으로 FastAPI에 **직접 연결**
3. FastAPI와 직접 통신 (Spring 경유 없음)

**Q: FastAPI 서버가 다운되면?**  
A: 앱은 FastAPI와 직접 연결하므로, FastAPI 다운 시 웹소켓 연결이 끊깁니다. 재연결 로직 구현 필요.

**Q: 토큰 갱신은 언제 하나요?**  
A: 토큰 만료 1-2분 전(13분 후) 자동으로 Spring API(`POST /api/session/{id}/refresh`)를 호출해 새 토큰을 받고, FastAPI에 재연결합니다.

**Q: 세션 완료는 언제 호출하나요?**  
A: 사용자가 분석을 종료할 때 Spring API(`POST /api/session/{id}/finish`)를 호출해 통계를 저장합니다.

**Q: Spring 서버와 FastAPI 서버가 다른 도메인이어도 되나요?**  
A: **네, 가능합니다**. Spring은 세션 발급만 하고, FastAPI는 별도 서버/도메인에서 운영 가능합니다.

```
