
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
* **응답 200(예시)**

```json
{
  "left": {
    "side":"left","stage":"DESCENT","status":"BAD",
    "feedback":"체중이 앞쪽으로 쏠렸습니다. 뒤꿈치로 눌러주세요. 무릎이 안쪽으로 모이고 있습니다.",
    "metrics":{"front":48.3,"rear":52.1,"inner":62.0,"outer":55.5,"heel":52.1,"innerOuterDiff":6.5}
  },
  "right": {
    "side":"right","stage":"ASCENT","status":"GOOD",
    "feedback":"상승 구간 자세가 안정적입니다. 그대로 일어나세요.",
    "metrics":{"front":50.2,"rear":49.7,"inner":49.8,"outer":50.1,"heel":49.7,"innerOuterDiff":0.3}
  }
}
```

* **판정 요약**:

  * **DESCENT BAD**: 앞쪽>40%, 안/바깥쪽>60%, 뒤꿈치 부족 등
  * **ASCENT BAD**: 뒤꿈치 <40%, 안/바깥쪽>60%, 앞·뒤 불균형
  * **NO_DATA**: 직전 10초 수집값 없음

### 5.5 AI + FSR 통합 피드백(REST)

* **GET** `/api/fsr_data/feedback/combined`
* **응답 200(예시)**

```json
{
  "ai": { "status": "OK", "message": "허리 정렬 양호" },
  "fsr": { "left": { "...": "FSRFeedbackSide" }, "right": { "...": "FSRFeedbackSide" } },
  "overallMessages": ["무릎이 안쪽으로 모입니다. 발바깥쪽으로 살짝 터치하세요.","뒤꿈치로 바닥을 눌러 균형을 맞추세요."]
}
```

---

## 6) 뷰모델 배선 샘플

```swift
@MainActor
final class PoseViewModel: ObservableObject {
    @Published var latest: FSRLatest?
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

    func requestCombinedFeedback() async {
        struct CombinedFeedback: Decodable {
            struct AI: Decodable { let status: String; let message: String? }
            struct FSR: Decodable { let left: FSRFeedbackSide?; let right: FSRFeedbackSide? }
            let ai: AI; let fsr: FSR; let overallMessages: [String]
        }
        do {
            let url = URL(string: "\(API.base)/api/fsr_data/feedback/combined")!
            let (data, _) = try await URLSession.shared.data(from: url)
            let c = try JSONDecoder().decode(CombinedFeedback.self, from: data)
            lastMessage = c.overallMessages.joined(separator: "\n")
        } catch { lastMessage = "피드백 실패: \(error.localizedDescription)" }
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

# 3) FSR 피드백
curl -s http://54.86.161.187:8080/api/fsr_data/feedback | jq

# 4) 세션 승격 (로그인 후)
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"userId":"<UUID>","sessionId":"<세션ID>","email":"user@example.com"}' \
  http://54.86.161.187:8080/auth/upgrade | jq

# 5) AI 상태 입력(내부)
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"lumbar":"good","knee":"bad","ankle":"null"}' \
  http://54.86.161.187:8080/internal/ai/status | jq
```
