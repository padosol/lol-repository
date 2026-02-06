# Stage 1: 빌드
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Gradle Wrapper 복사 및 실행 권한 부여
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew

# 의존성 캐싱을 위해 빌드 파일 먼저 복사
COPY build.gradle settings.gradle ./
COPY app app
COPY core core
COPY infra infra
COPY support support

# 빌드 실행
RUN ./gradlew :app:bootJar -x test --no-daemon

# Stage 2: 런타임
FROM eclipse-temurin:17-jre
WORKDIR /app

# 환경변수 선언
ENV SPRING_PROFILES_ACTIVE=dev

# JAR 복사
COPY --from=build /app/app/build/libs/*.jar app.jar

# JVM 컨테이너 최적화 옵션
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 쉘 형식으로 변경하여 환경 변수 확장 지원
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar app.jar"]
