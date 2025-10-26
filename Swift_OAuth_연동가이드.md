# Swift 앱 OAuth 로그인 연동 가이드

## 📋 현재 백엔드 OAuth 구조

### 지원하는 OAuth 제공자
- ✅ Kakao
- ✅ Naver  
- ✅ Google
- ✅ Apple

### 인증 방식
- **JWT 토큰 기반 인증**
- Access Token + Refresh Token 발급

---

## 🔄 OAuth 로그인 플로우

```
1. Swift 앱 → 백엔드 OAuth URL 호출 (웹뷰)
2. 백엔드 → OAuth 제공자 로그인 페이지로 리다이렉트
3. 사용자 → OAuth 제공자에서 로그인 및 동의
4. OAuth 제공자 → 백엔드로 콜백
5. 백엔드 → JWT 토큰 생성 후 앱 URL 스킴으로 리다이렉트
6. Swift 앱 → 토큰 수신 및 저장
```

---

## 📡 API 엔드포인트

### 1. OAuth 로그인 시작 URL

각 OAuth 제공자별로 로그인을 시작하는 URL:

```swift
// 기본 URL
let baseURL = "http://52.91.9.98:8080"

// 각 제공자별 OAuth 시작 URL
let kakaoLoginURL = "\(baseURL)/oauth2/authorization/kakao"
let naverLoginURL = "\(baseURL)/oauth2/authorization/naver"
let googleLoginURL = "\(baseURL)/oauth2/authorization/google"
let appleLoginURL = "\(baseURL)/oauth2/authorization/apple"
```

#### Query Parameters (선택사항):
```
?redirect_uri=squirret://auth/callback
```
- 미지정 시 기본값: `squirret://auth/callback`

---

### 2. OAuth 콜백 (자동 처리됨)

백엔드가 자동으로 처리하는 콜백 URL (앱에서 호출 불필요):
```
http://52.91.9.98:8080/login/oauth2/code/{provider}
```

---

### 3. 최종 리다이렉트 (앱이 수신)

로그인 성공 시 앱의 URL 스킴으로 리다이렉트:

```
squirret://auth/callback?access_token={JWT_TOKEN}&refresh_token={REFRESH_TOKEN}
```

#### 응답 파라미터:
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `access_token` | String | JWT 액세스 토큰 (유효기간: 24시간) |
| `refresh_token` | String | JWT 리프레시 토큰 (유효기간: 7일) |

---

### 4. 사용자 정보 조회 API

#### Endpoint: `GET /api/auth/me`

**Headers:**
```
Authorization: Bearer {access_token}
```

**응답 (Success 200):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "홍길동",
  "provider": "KAKAO",
  "profileImageUrl": "https://..."
}
```

---

### 5. 토큰 갱신 API

#### Endpoint: `POST /api/auth/refresh`

**Request Body:**
```json
{
  "refreshToken": "{refresh_token}"
}
```

**응답 (Success 200):**
```json
{
  "accessToken": "{new_access_token}",
  "refreshToken": "{new_refresh_token}",
  "expiresIn": 86400
}
```

---

### 6. 로그아웃 API

#### Endpoint: `POST /api/auth/logout`

**Headers:**
```
Authorization: Bearer {access_token}
```

**응답 (Success 200):**
```json
{
  "message": "Logged out successfully"
}
```

> **참고**: JWT는 stateless이므로 클라이언트에서 토큰을 삭제하는 것만으로도 충분합니다.

---

## 📱 Swift 구현 예제

### 1. ASWebAuthenticationSession을 사용한 OAuth 로그인

```swift
import AuthenticationServices

class OAuthManager {
    static let shared = OAuthManager()
    
    private let baseURL = "http://52.91.9.98:8080"
    private let callbackURLScheme = "squirret"
    
    // MARK: - OAuth 로그인
    
    func loginWithKakao(completion: @escaping (Result<OAuthTokens, Error>) -> Void) {
        login(provider: "kakao", completion: completion)
    }
    
    func loginWithNaver(completion: @escaping (Result<OAuthTokens, Error>) -> Void) {
        login(provider: "naver", completion: completion)
    }
    
    func loginWithGoogle(completion: @escaping (Result<OAuthTokens, Error>) -> Void) {
        login(provider: "google", completion: completion)
    }
    
    func loginWithApple(completion: @escaping (Result<OAuthTokens, Error>) -> Void) {
        login(provider: "apple", completion: completion)
    }
    
    // MARK: - Private Methods
    
    private func login(provider: String, completion: @escaping (Result<OAuthTokens, Error>) -> Void) {
        // OAuth URL 생성
        guard let url = URL(string: "\(baseURL)/oauth2/authorization/\(provider)") else {
            completion(.failure(OAuthError.invalidURL))
            return
        }
        
        // ASWebAuthenticationSession 시작
        let session = ASWebAuthenticationSession(
            url: url,
            callbackURLScheme: callbackURLScheme
        ) { callbackURL, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let callbackURL = callbackURL else {
                completion(.failure(OAuthError.noCallbackURL))
                return
            }
            
            // 토큰 추출
            if let tokens = self.extractTokens(from: callbackURL) {
                // 토큰 저장
                self.saveTokens(tokens)
                completion(.success(tokens))
            } else {
                completion(.failure(OAuthError.tokenExtractionFailed))
            }
        }
        
        session.presentationContextProvider = self
        session.prefersEphemeralWebBrowserSession = false
        session.start()
    }
    
    // URL에서 토큰 추출
    private func extractTokens(from url: URL) -> OAuthTokens? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let queryItems = components.queryItems else {
            return nil
        }
        
        var accessToken: String?
        var refreshToken: String?
        
        for item in queryItems {
            switch item.name {
            case "access_token":
                accessToken = item.value
            case "refresh_token":
                refreshToken = item.value
            default:
                break
            }
        }
        
        guard let access = accessToken, let refresh = refreshToken else {
            return nil
        }
        
        return OAuthTokens(accessToken: access, refreshToken: refresh)
    }
    
    // 토큰 저장 (Keychain 사용 권장)
    private func saveTokens(_ tokens: OAuthTokens) {
        KeychainManager.shared.save(tokens.accessToken, for: "accessToken")
        KeychainManager.shared.save(tokens.refreshToken, for: "refreshToken")
    }
}

// MARK: - ASWebAuthenticationPresentationContextProviding

extension OAuthManager: ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        return UIApplication.shared.windows.first { $0.isKeyWindow } ?? ASPresentationAnchor()
    }
}

// MARK: - Models

struct OAuthTokens {
    let accessToken: String
    let refreshToken: String
}

enum OAuthError: Error {
    case invalidURL
    case noCallbackURL
    case tokenExtractionFailed
}
```

---

### 2. API 호출 예제 (사용자 정보 조회)

```swift
class APIManager {
    static let shared = APIManager()
    
    private let baseURL = "http://52.91.9.98:8080"
    
    func fetchUserInfo(completion: @escaping (Result<User, Error>) -> Void) {
        guard let accessToken = KeychainManager.shared.load(for: "accessToken") else {
            completion(.failure(APIError.noToken))
            return
        }
        
        guard let url = URL(string: "\(baseURL)/api/auth/me") else {
            completion(.failure(APIError.invalidURL))
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let data = data else {
                completion(.failure(APIError.noData))
                return
            }
            
            do {
                let user = try JSONDecoder().decode(User.self, from: data)
                completion(.success(user))
            } catch {
                completion(.failure(error))
            }
        }.resume()
    }
}

// MARK: - Models

struct User: Codable {
    let id: String
    let email: String?
    let name: String?
    let provider: String
    let profileImageUrl: String?
}

enum APIError: Error {
    case invalidURL
    case noToken
    case noData
}
```

---

### 3. 토큰 갱신 예제

```swift
extension APIManager {
    func refreshToken(completion: @escaping (Result<OAuthTokens, Error>) -> Void) {
        guard let refreshToken = KeychainManager.shared.load(for: "refreshToken") else {
            completion(.failure(APIError.noToken))
            return
        }
        
        guard let url = URL(string: "\(baseURL)/api/auth/refresh") else {
            completion(.failure(APIError.invalidURL))
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body = ["refreshToken": refreshToken]
        request.httpBody = try? JSONEncoder().encode(body)
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let data = data else {
                completion(.failure(APIError.noData))
                return
            }
            
            do {
                let response = try JSONDecoder().decode(RefreshTokenResponse.self, from: data)
                let tokens = OAuthTokens(
                    accessToken: response.accessToken,
                    refreshToken: response.refreshToken
                )
                
                // 새 토큰 저장
                KeychainManager.shared.save(tokens.accessToken, for: "accessToken")
                KeychainManager.shared.save(tokens.refreshToken, for: "refreshToken")
                
                completion(.success(tokens))
            } catch {
                completion(.failure(error))
            }
        }.resume()
    }
}

struct RefreshTokenResponse: Codable {
    let accessToken: String
    let refreshToken: String
    let expiresIn: Int
}
```

---

### 4. Keychain 매니저 (토큰 안전 저장)

```swift
import Security

class KeychainManager {
    static let shared = KeychainManager()
    
    func save(_ value: String, for key: String) {
        let data = Data(value.utf8)
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecValueData as String: data
        ]
        
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }
    
    func load(for key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true
        ]
        
        var result: AnyObject?
        SecItemCopyMatching(query as CFDictionary, &result)
        
        guard let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
    
    func delete(for key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key
        ]
        
        SecItemDelete(query as CFDictionary)
    }
}
```

---

### 5. SwiftUI 로그인 화면 예제

```swift
import SwiftUI

struct LoginView: View {
    @StateObject private var viewModel = LoginViewModel()
    
    var body: some View {
        VStack(spacing: 20) {
            Text("Squirret")
                .font(.largeTitle)
                .fontWeight(.bold)
            
            Text("소셜 로그인으로 시작하기")
                .font(.subheadline)
                .foregroundColor(.gray)
            
            Spacer()
            
            // Kakao 로그인
            Button {
                viewModel.loginWithKakao()
            } label: {
                HStack {
                    Image(systemName: "message.fill")
                    Text("카카오로 로그인")
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.yellow)
                .foregroundColor(.black)
                .cornerRadius(10)
            }
            
            // Naver 로그인
            Button {
                viewModel.loginWithNaver()
            } label: {
                HStack {
                    Image(systemName: "n.circle.fill")
                    Text("네이버로 로그인")
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.green)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
            
            // Google 로그인
            Button {
                viewModel.loginWithGoogle()
            } label: {
                HStack {
                    Image(systemName: "g.circle.fill")
                    Text("구글로 로그인")
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.white)
                .foregroundColor(.black)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.gray, lineWidth: 1)
                )
                .cornerRadius(10)
            }
            
            // Apple 로그인
            Button {
                viewModel.loginWithApple()
            } label: {
                HStack {
                    Image(systemName: "applelogo")
                    Text("Apple로 로그인")
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.black)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
            
            Spacer()
        }
        .padding()
        .alert("로그인 오류", isPresented: $viewModel.showError) {
            Button("확인", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage)
        }
    }
}

class LoginViewModel: ObservableObject {
    @Published var showError = false
    @Published var errorMessage = ""
    
    func loginWithKakao() {
        OAuthManager.shared.loginWithKakao { [weak self] result in
            DispatchQueue.main.async {
                self?.handleLoginResult(result)
            }
        }
    }
    
    func loginWithNaver() {
        OAuthManager.shared.loginWithNaver { [weak self] result in
            DispatchQueue.main.async {
                self?.handleLoginResult(result)
            }
        }
    }
    
    func loginWithGoogle() {
        OAuthManager.shared.loginWithGoogle { [weak self] result in
            DispatchQueue.main.async {
                self?.handleLoginResult(result)
            }
        }
    }
    
    func loginWithApple() {
        OAuthManager.shared.loginWithApple { [weak self] result in
            DispatchQueue.main.async {
                self?.handleLoginResult(result)
            }
        }
    }
    
    private func handleLoginResult(_ result: Result<OAuthTokens, Error>) {
        switch result {
        case .success(let tokens):
            print("로그인 성공!")
            print("Access Token: \(tokens.accessToken)")
            // 메인 화면으로 이동
            
        case .failure(let error):
            showError = true
            errorMessage = error.localizedDescription
        }
    }
}
```

---

## 📝 Info.plist 설정

앱에서 URL 스킴을 받기 위한 설정:

```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>squirret</string>
        </array>
        <key>CFBundleURLName</key>
        <string>com.squirret.app</string>
    </dict>
</array>
```

---

## 🔒 보안 권장사항

1. **HTTPS 사용**: 프로덕션에서는 반드시 HTTPS 사용
2. **Keychain 사용**: 토큰은 Keychain에 안전하게 저장
3. **토큰 검증**: API 호출 시 401 에러 시 자동으로 refresh token 사용
4. **Refresh Token 만료**: Refresh token도 만료되면 재로그인 필요

---

## 🎯 요약

### OAuth 로그인 URL:
```
http://52.91.9.98:8080/oauth2/authorization/{provider}
```

### 콜백 URL 스킴:
```
squirret://auth/callback?access_token={TOKEN}&refresh_token={TOKEN}
```

### 인증이 필요한 API 헤더:
```
Authorization: Bearer {access_token}
```

### 주요 API:
- `GET /api/auth/me` - 사용자 정보
- `POST /api/auth/refresh` - 토큰 갱신
- `POST /api/auth/logout` - 로그아웃

