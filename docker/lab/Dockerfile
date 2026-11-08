# Riot API 실험실 이미지. 저장소 루트를 context 로 소스에서 jar 를 빌드한다 (호스트에 JDK·Gradle 이 없어도 된다).
# target app = 앱(lol-lab-app), target riot-mock = 가짜 Riot 서버. 두 target 이 build 단계를 같이 쓴다.
# 같은 버전 태그도 OS 패치로 다시 빌드되므로 검증한 이미지를 다이제스트로 고정한다.

FROM eclipse-temurin:21.0.11_10-jdk@sha256:98155457fcdbeb0987472e8abf721657b698ede407aad52ac24ae42618c0931c AS build
WORKDIR /src
COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew
COPY build.gradle settings.gradle ./
COPY buildSrc buildSrc
COPY module module
# settings.gradle 이 include 하는 프로젝트 디렉터리는 모두 있어야 한다 (없으면 Gradle 설정 단계에서 실패)
COPY tools tools
# /root/.gradle(배포판·의존성·빌드 캐시)은 빌드 사이에 남는다. --build-cache 로 바뀌지 않은 모듈은 컴파일 결과를 다시 쓴다.
# bootJar 만 돌리므로 jar 는 하나씩이다. 여럿이면 cp 가 실패해 빌드가 멈춘다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --build-cache :module:app:application:bootJar :tools:riot-mock:bootJar \
 && mkdir /out \
 && cp module/app/application/build/libs/*.jar /out/app.jar \
 && cp tools/riot-mock/build/libs/riot-mock.jar /out/riot-mock.jar

FROM eclipse-temurin:21.0.11_10-jre@sha256:d2b9f8f12212cadcfdf889461531784e8fd097feade954d65b31ee7a71c473ec AS app
COPY --from=build /out/app.jar /app/app.jar
EXPOSE 8111 8112
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

FROM eclipse-temurin:21.0.11_10-jre@sha256:d2b9f8f12212cadcfdf889461531784e8fd097feade954d65b31ee7a71c473ec AS riot-mock
COPY --from=build /out/riot-mock.jar /app/riot-mock.jar
EXPOSE 8099
ENTRYPOINT ["java", "-jar", "/app/riot-mock.jar"]
