# Swagger 사용 가이드

## 1. 설정 완료

Swagger UI를 사용하기 위한 설정이 완료되었습니다:

- ✅ `build.gradle`에 SpringDoc OpenAPI 의존성 추가
- ✅ `OpenApiConfig.java` 설정 클래스 생성
- ✅ `application.yaml`에 Swagger 설정 추가

## 2. 애플리케이션 실행

```bash
# 의존성 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

## 3. Swagger UI 접속

애플리케이션 실행 후 다음 URL로 접속:

### 로컬 개발 환경
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

### 프로덕션 환경
- **Swagger UI**: http://54.86.161.187:8080/swagger-ui.html
- **OpenAPI JSON**: http://54.86.161.187:8080/v3/api-docs

## 4. Swagger UI 사용 방법

1. **브라우저에서 접속**: `http://localhost:8080/swagger-ui.html`
2. **API 목록 확인**: 왼쪽 사이드바에서 태그별로 그룹화된 API 확인
3. **API 테스트**:
   - 원하는 API 클릭
   - "Try it out" 버튼 클릭
   - 필요한 파라미터 입력
   - "Execute" 버튼 클릭
   - 응답 결과 확인

## 5. 주요 기능

### API 그룹화
- **Guest**: 게스트 세션 관리
- **Session**: 세션 및 인증 관리
- **FSR**: FSR(깔창) 데이터 관리
- **Inference**: AI 추론 세션 관리
- **Internal**: 내부 API

### 스키마 확인
- 각 API의 요청/응답 스키마 확인 가능
- 예시 값 확인 가능
- 필수/선택 필드 구분

## 6. OpenAPI JSON 다운로드

OpenAPI JSON 스펙을 다운로드하여 다른 도구에서 사용 가능:

```bash
# OpenAPI JSON 다운로드
curl http://localhost:8080/v3/api-docs > openapi.json

# 또는 YAML 형식으로 변환
curl http://localhost:8080/v3/api-docs.yaml > openapi.yaml
```

## 7. 다른 도구에서 사용

### Postman
1. Postman 열기
2. Import → Link
3. `http://localhost:8080/v3/api-docs` 입력
4. Import 클릭

### Insomnia
1. Insomnia 열기
2. Create → Import/Export → Import Data → From URL
3. `http://localhost:8080/v3/api-docs` 입력

### 코드 생성
- OpenAPI Generator를 사용하여 클라이언트 코드 생성 가능
- 다양한 언어 지원 (TypeScript, Swift, Python 등)

## 8. 문제 해결

### Swagger UI가 보이지 않는 경우
1. 애플리케이션이 정상적으로 실행되었는지 확인
2. 포트 8080이 사용 중인지 확인
3. 브라우저 콘솔에서 에러 확인

### API가 Swagger에 표시되지 않는 경우
1. 컨트롤러에 `@RestController` 또는 `@Controller` 어노테이션 확인
2. 메서드에 `@GetMapping`, `@PostMapping` 등 어노테이션 확인
3. 패키지 스캔 경로 확인

## 9. 추가 설정 (선택사항)

### 특정 경로만 Swagger에 표시
`application.yaml`에 추가:
```yaml
springdoc:
  paths-to-match: /api/**, /internal/**
```

### Swagger UI 경로 변경
```yaml
springdoc:
  swagger-ui:
    path: /api-docs
```

### 프로덕션에서 Swagger 비활성화
```yaml
springdoc:
  swagger-ui:
    enabled: false
```

