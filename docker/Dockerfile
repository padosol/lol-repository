
# Stage 1: 빌드
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle Wrapper 복사 및 실행 권한 부여
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew

# 의존성 캐싱을 위해 빌드 파일 먼저 복사
COPY build.gradle settings.gradle ./
COPY buildSrc buildSrc
COPY module module
# settings.gradle 이 include 하는 프로젝트 디렉터리는 모두 있어야 한다 (없으면 Gradle 이 설정 단계에서 실패). 런타임 이미지에는 앱 jar 만 들어간다
COPY tools tools

# 빌드 실행
RUN ./gradlew :module:app:application:bootJar -x test --no-daemon

# Stage 2: 런타임
FROM eclipse-temurin:21-jre
WORKDIR /app

# 환경변수 선언
ENV SPRING_PROFILES_ACTIVE=dev

# JAR 복사
COPY --from=build /app/module/app/application/build/libs/*.jar app.jar

# JVM 컨테이너 최적화 옵션
# 힙 상한은 이미지가 아니라 배포 쪽(lol-deploy jvmOptions → JAVA_TOOL_OPTIONS 의 -Xmx)에서 정한다.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=50.0"
ENV JVM_DIAGNOSTIC_OPTS=""

# 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 쉘 형식으로 변경하여 환경 변수 확장 지원
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $JVM_DIAGNOSTIC_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar app.jar"]
