# Spring Boot OAuth(애플·카카오·네이버) 스타터 킷

이 프로젝트는 Spring Boot 3.4.x와 Java 17을 사용하여 카카오, 네이버, 애플 OAuth 로그인을 구현한 백엔드 애플리케이션입니다.

## 주요 기능

- 카카오 OAuth 로그인
- 네이버 OAuth 로그인  
- 애플 OAuth 로그인
- JWT 토큰 기반 인증
- 사용자 정보 관리

## 프로젝트 구조

```
src/main/java/com/squirret/squirretbackend/
├── controller/
│   └── AuthController.java          # OAuth 로그인 및 인증 관련 API
├── entity/
│   └── User.java                    # 사용자 엔티티
├── repository/
│   └── UserRepository.java          # 사용자 데이터 접근
├── security/
│   ├── CustomOAuth2UserService.java # OAuth 사용자 정보 처리
│   ├── AppleClientSecretUtil.java   # 애플 JWT 생성 유틸리티
│   ├── JwtAuthenticationFilter.java # JWT 인증 필터
│   └── JwtService.java             # JWT 토큰 서비스
├── service/
│   ├── UserService.java            # 사용자 비즈니스 로직
│   └── CustomUserDetailsService.java
└── dto/
    └── AuthResponseDto.java        # 인증 응답 DTO
```

## 설정 방법

### 1. 환경변수 설정

다음 환경변수들을 설정해야 합니다:

#### Windows PowerShell:
```powershell
$env:KAKAO_CLIENT_ID="your_kakao_client_id"
$env:KAKAO_CLIENT_SECRET="your_kakao_client_secret"
$env:NAVER_CLIENT_ID="your_naver_client_id"
$env:NAVER_CLIENT_SECRET="your_naver_client_secret"
$env:APPLE_SERVICE_ID="your_apple_service_id"
$env:APPLE_CLIENT_SECRET_JWT="your_apple_jwt_token"
```

#### macOS/Linux:
```bash
export KAKAO_CLIENT_ID="your_kakao_client_id"
export KAKAO_CLIENT_SECRET="your_kakao_client_secret"
export NAVER_CLIENT_ID="your_naver_client_id"
export NAVER_CLIENT_SECRET="your_naver_client_secret"
export APPLE_SERVICE_ID="your_apple_service_id"
export APPLE_CLIENT_SECRET_JWT="your_apple_jwt_token"
```

### 2. OAuth 공급자 설정

#### 카카오 개발자 콘솔
1. [카카오 개발자 콘솔](https://developers.kakao.com/) 접속
2. 애플리케이션 생성
3. REST API 키를 `KAKAO_CLIENT_ID`로 설정
4. 보안 > Client Secret을 `KAKAO_CLIENT_SECRET`으로 설정
5. 제품 설정 > 카카오 로그인 > Redirect URI 등록: `http://localhost:8080/login/oauth2/code/kakao`
6. 동의항목에서 이메일, 프로필 정보 동의 설정

#### 네이버 개발자 센터
1. [네이버 개발자 센터](https://developers.naver.com/) 접속
2. 애플리케이션 등록
3. Client ID를 `NAVER_CLIENT_ID`로 설정
4. Client Secret을 `NAVER_CLIENT_SECRET`으로 설정
5. 서비스 환경 > PC 웹 > 서비스 URL: `http://localhost:8080`
6. Callback URL: `http://localhost:8080/login/oauth2/code/naver`

#### 애플 개발자 콘솔
1. [애플 개발자 콘솔](https://developer.apple.com/) 접속
2. Certificates, Identifiers & Profiles > Identifiers에서 Service ID 생성
3. Service ID를 `APPLE_SERVICE_ID`로 설정
4. Keys에서 새로운 키 생성 후 p8 파일 다운로드
5. Team ID와 Key ID 확인
6. `AppleClientSecretUtil.create()` 메서드로 JWT 토큰 생성하여 `APPLE_CLIENT_SECRET_JWT`로 설정

### 3. 데이터베이스 설정

MySQL 데이터베이스를 사용합니다. `application.yml`에서 데이터베이스 연결 정보를 설정하세요.

## 실행 방법

```bash
# 의존성 설치
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

## API 엔드포인트

### OAuth 로그인
- `GET /` - 로그인 페이지 (카카오, 네이버, 애플 로그인 링크)
- `GET /oauth2/authorization/kakao` - 카카오 로그인
- `GET /oauth2/authorization/naver` - 네이버 로그인
- `GET /oauth2/authorization/apple` - 애플 로그인

### 사용자 정보
- `GET /me` - 현재 로그인한 사용자 정보 (세션 기반)
- `GET /api/auth/me` - 현재 로그인한 사용자 정보 (JWT 기반)

### 토큰 관리
- `POST /api/auth/refresh` - JWT 토큰 갱신
- `POST /api/auth/logout` - 로그아웃

## 테스트 방법

1. 애플리케이션 실행 후 `http://localhost:8080/` 접속
2. 원하는 OAuth 공급자 로그인 버튼 클릭
3. OAuth 공급자에서 로그인 및 동의
4. 콜백 후 `/me` 엔드포인트에서 사용자 정보 확인

## 주의사항

1. **애플 OAuth**: `client_secret`은 JWT 토큰으로 생성되며 6개월마다 갱신해야 합니다.
2. **HTTPS**: 실제 서비스에서는 HTTPS를 사용해야 합니다.
3. **도메인**: 애플 OAuth는 도메인이 고정되어야 합니다.
4. **데이터 저장**: 최초 로그인 시 받은 이름/이메일 정보를 영구 저장해야 합니다.

## 문제 해결

### 일반적인 오류
- 환경변수가 제대로 설정되지 않은 경우
- OAuth 공급자 콘솔에서 리다이렉트 URI가 잘못 설정된 경우
- 애플 JWT 토큰이 만료된 경우

### 로그 확인
```bash
# 애플리케이션 로그에서 OAuth 관련 오류 확인
tail -f logs/application.log
```
