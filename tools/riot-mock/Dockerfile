# JRE 21.0.11+10. 같은 버전 태그도 OS 패치로 다시 빌드돼 바뀌므로, 실험실에서 검증한 이미지를 다이제스트로 고정한다.
FROM eclipse-temurin:21.0.11_10-jre@sha256:d2b9f8f12212cadcfdf889461531784e8fd097feade954d65b31ee7a71c473ec
COPY build/libs/riot-mock.jar /app/riot-mock.jar
EXPOSE 8099
ENTRYPOINT ["java", "-jar", "/app/riot-mock.jar"]
