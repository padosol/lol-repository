
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
COPY lol-db-schema lol-db-schema

# 서브모듈 가드 — lol-db-schema 가 비어 있어도 COPY 는 에러 없이 통과한다.
# 그대로 두면 마이그레이션 0개인 이미지가 조용히 만들어지고, Flyway 는
# "No migrations found" 만 남긴 채 정상 기동해 배포는 초록불인데 스키마만
# 그대로인 상태가 된다. 빌드 시점에 끊는다.
RUN test -n "$(ls -A lol-db-schema/db/migration/*.sql 2>/dev/null)" \
    || (echo "ERROR: lol-db-schema 서브모듈이 비어 있습니다 — 마이그레이션 없이 이미지가 만들어집니다. checkout 의 submodules 설정과 PAT 유효성을 확인하세요." >&2 && exit 1)

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
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
ENV JVM_DIAGNOSTIC_OPTS=""

# 포트 노출
EXPOSE 8080

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 쉘 형식으로 변경하여 환경 변수 확장 지원
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $JVM_DIAGNOSTIC_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar app.jar"]
