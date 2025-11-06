# Squirret WebSocket/STOMP & 세션 API 테스트 가이드

본 문서는 로컬/서버 환경에서 세션 발급 → WebSocket 연결 → STOMP 메시지 송수신 → 전환(guest→user) 흐름을 검증하는 방법을 설명합니다.

## 0. 준비물
- Java 17, Gradle
- Docker (MySQL 및 앱을 Docker로 띄우는 경우)
- 웹소켓/스톰프 테스트 도구 중 하나
  - Node: `wscat` (원시 STOMP 프레임 전송 가능) 또는
  - Python: `websockets` + 수동 프레임 또는
  - GUI: Insomnia/WebSocket 탭 (원시 프레임 전송)

> 참고: 우리 서버는 SockJS 미사용. 순수 WebSocket + STOMP 프레임입니다.

## 1. 서버 실행

### (A) Docker Compose로 실행
```bash
cd /home/ec2-user/Squirret_Backend
# MySQL + 앱 동시에 기동
docker compose up -d --build

# 로그 확인 (선택)
docker logs -f squirret-app
```

### (B) 로컬에서 앱만 실행(외부 MySQL 사용 시)
```bash
./gradlew bootRun
```

앱 포트: 8080, WebSocket 엔드포인트: `ws://<host>:8080/ws`

## 2. 세션 발급 (REST)
게스트 세션 토큰(`wsToken`)과 `sessionId`를 발급받습니다.

```bash
curl -s -X POST \
  -H "Content-Type: application/json" \
  http://<host>:8080/internal/session | jq
```

응답 예시:
```json
{
  "sessionId": "e0e1c6af-...",
  "wsToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

이제 `wsToken`으로 WebSocket에 접속합니다.

## 3. WebSocket STOMP 연결
원시 STOMP 프레임을 전송할 수 있는 도구(예: `wscat`)로 테스트합니다.

### 3.1 설치(선택)
```bash
npm i -g wscat
```

### 3.2 접속
```bash
wscat -c "ws://<host>:8080/ws?token=<wsToken>"
```
연결되면, STOMP CONNECT 프레임을 전송합니다. `wscat`에 아래 텍스트를 붙여 넣으세요.

```
CONNECT\naccept-version:1.2\nhost:localhost\n\n\u0000
```

서버가 `CONNECTED`로 응답하면 구독을 등록합니다(개인 큐).

```
SUBSCRIBE\nid:sub-1\ndestination:/user/queue/session\n\n\u0000
```

이제 앱 대상 라우팅(`/app/session.message`)으로 메시지를 전송합니다.

```
SEND\ndestination:/app/session.message\ncontent-type:application/json\n\n{"type":"PING","payload":{"ts":123}}\u0000
```

정상이라면 개인 큐(`/user/queue/session`)로 echo 응답이 수신됩니다. 또한 수신 페이로드는 DB 테이블 `ws_message_log`에 저장됩니다.

> 주의: STOMP 프레임의 끝에는 널 문자 `\u0000`가 필요합니다.

## 4. 전환(guest → user)
로그인 후 유저 토큰으로 승격하는 흐름을 검증합니다.

```bash
curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"userId":"<UUID>","sessionId":"<세션ID>", "email":"user@example.com"}' \
  http://<host>:8080/auth/upgrade | jq
```

응답 예시:
```json
{
  "sessionId": "e0e1c6af-...",
  "wsToken": "<새로운 유저 토큰>"
}
```
이제 새 `wsToken`으로 WebSocket을 재연결하면, 사용자 권한 컨텍스트로 동작합니다.

## 5. DB 확인 (선택)
`ws_message_log`에 수신 메시지가 적재됩니다.

```bash
# Docker MySQL 컨테이너 안에서 확인
docker exec -it squirret-mysql mysql -uroot -p1234 -e "USE squirretDB; SELECT id, actor_id, type, LEFT(payload,120) AS snippet, created_at FROM ws_message_log ORDER BY id DESC LIMIT 10;"
```

## 6. 문제 해결 팁
- 401/연결 거부: `token`이 쿼리 스트링으로 전달되었는지(예: `?token=...`) 확인
- STOMP 미응답: `CONNECT`/`SUBSCRIBE`/`SEND` 프레임에 `\u0000`(널 문자) 누락 여부 확인
- CORS/오리진: 서버는 `AllowedOriginPatterns=*`로 설정되어 있으나, 브라우저 환경이라면 HTTPS/오리진 매칭을 고려
- 포트: 서버(8080) 및 MySQL(3306)이 보안 그룹/방화벽에서 개방되었는지 확인

## 7. 빠른 체크리스트
- [ ] `/internal/session` 호출로 `wsToken` 확보
- [ ] `ws://<host>:8080/ws?token=...` 접속
- [ ] STOMP `CONNECT` → `SUBSCRIBE`(`/user/queue/session`) → `SEND`(`/app/session.message`)
- [ ] 응답 수신 및 `ws_message_log` 적재 확인
- [ ] `/auth/upgrade`로 유저 토큰 발급 후 재연결

---
문의/보완 요청은 이 레포 이슈로 남겨주세요.

---

## 8. iOS 앱 연동 가이드(실전)

아래는 iOS에서 “로그인 없이” 게스트 세션으로 연결하고, 서버가 AI 서버로부터 데이터를 받아오면 1초마다 데이터, 10초마다 피드백을 앱으로 푸시받는 사용 예입니다.

### 8.1 의존성(SPM)
- 패키지 추가: `https://github.com/WrathChaos/StompClientLib.git`

### 8.2 세션 발급(REST) → 토큰 획득
```swift
struct SessionIssueResp: Decodable { let sessionId: String; let wsToken: String }

func issueSession(completion: @escaping (SessionIssueResp?) -> Void) {
    var req = URLRequest(url: URL(string: "https://<host>:8080/internal/session")!)
    req.httpMethod = "POST"
    req.addValue("application/json", forHTTPHeaderField: "Content-Type")
    URLSession.shared.dataTask(with: req) { data, _, _ in
        let resp = data.flatMap { try? JSONDecoder().decode(SessionIssueResp.self, from: $0) }
        completion(resp)
    }.resume()
}
```

### 8.3 WebSocket/STOMP 연결 및 구독/송신
```swift
import StompClientLib

final class WSService: NSObject, StompClientLibDelegate {
    private let stomp = StompClientLib()
    private var request: NSURLRequest!

    func connect(wsToken: String) {
        let url = NSURL(string: "wss://<host>:8080/ws?token=\(wsToken)")!
        request = NSURLRequest(url: url as URL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10)
        stomp.openSocketWithURLRequest(request: request, delegate: self)
    }

    func stompClientDidConnect(client: StompClientLib!) {
        client.subscribe(destination: "/user/queue/session")
        // 서버와 초기 동기화용 핑
        let msg: [String: Any] = ["type":"PING", "payload":["ts": Date().timeIntervalSince1970]]
        client.sendJSONForDict(dict: msg as NSDictionary, toDestination: "/app/session.message")
    }

    func stompClient(_ client: StompClientLib!, didReceiveMessageWithJSONBody jsonBody: AnyObject?, akaStringBody stringBody: String?, withHeader header: [String : String]?, withDestination destination: String) {
        // 서버가 1초마다 DATA, 10초마다 FEEDBACK 타입으로 푸시
        // stringBody(JSON) 파싱 후 UI 업데이트
        print("recv \(destination): \(stringBody ?? "")")
    }

    func serverDidSendError(client: StompClientLib!, withErrorMessage description: String, detailedErrorMessage: String?) {
        reconnect()
    }

    private func reconnect(delay: TimeInterval = 1.0) {
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self = self else { return }
            self.stomp.openSocketWithURLRequest(request: self.request, delegate: self)
        }
    }
}

// 사용 예
let ws = WSService()
issueSession { resp in
    guard let resp = resp else { return }
    ws.connect(wsToken: resp.wsToken)
}
```

### 8.4 서버가 푸시하는 메시지 규격(예시)
- 1초마다 데이터(DATA)
```json
{ "type": "DATA", "payload": { "value": 0.123, "ts": 1730892345, "ai": { "lumbar": "good|bad|null", "knee": "good|bad|null", "ankle": "good|bad|null" } } }
```
- 10초마다 피드백(요청 포맷 적용)
```json
{ "type": "voice", "text": "뒷꿈치를 좀 더 누르세요" }
```
서버는 위 메시지를 `/user/queue/session` 개인 큐로 전송하므로, 앱은 해당 구독만 유지하면 주기적으로 수신합니다.

> 현재 서버는 더미 타이머가 활성화되어 연결된 사용자에게 자동으로 DATA(1초), FEEDBACK(10초)을 전송합니다. 배포 환경에서 비활성화하려면 `FeedbackPushService`의 `@Scheduled` 주석을 제거하거나 프로파일 조건을 적용하세요.

### 8.7 AI 상태 입력 API (서버 내부 연동용)
AI 서버는 다음 엔드포인트로 현재 상태를 전송하세요. 서버는 최신 상태를 저장하고, 다음 주기 DATA/FEEDBACK에 반영합니다.

```
POST /internal/ai/status
Content-Type: application/json

{
  "lumbar": "good|bad|null",
  "knee": "good|bad|null",
  "ankle": "good|bad|null"
}
```
유효하지 않은 값이 들어오면 400(Bad Request)을 반환합니다.

### 8.5(선택) 앱→서버 이벤트 전송
앱에서 상태/제어를 서버로 보내고 싶으면 `/app/session.message`로 JSON을 전송하세요.
```swift
let cmd: [String: Any] = [
  "type": "CLIENT_EVENT",
  "payload": ["name": "button.click", "ts": Date().timeIntervalSince1970]
]
stomp.sendJSONForDict(dict: cmd as NSDictionary, toDestination: "/app/session.message")
```

### 8.6 운영 팁
- 네트워크 변동 시 자동 재연결(backoff) 유지
- 백그라운드 전환 시 구독 재등록 필요할 수 있음
- 실서비스는 `https/wss` 권장 및 보안 그룹(8080) 오픈 확인

