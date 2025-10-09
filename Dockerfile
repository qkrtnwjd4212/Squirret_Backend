# 빌드 스테이지
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Gradle 캐시를 위한 의존성 파일 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# 소스 코드 복사
COPY src ./src

# 애플리케이션 빌드 (테스트 제외)
RUN gradle build -x test --no-daemon

# 실행 스테이지
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 환경 변수 설정 (기본값)
ENV SPRING_PROFILES_ACTIVE=prod

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]

