FROM bellsoft/liberica-openjdk-alpine:17

RUN apk add --no-cache curl

ARG JAR_FILE=build/libs/*.jar

COPY ${JAR_FILE} app.jar

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java","-jar","-Dspring.profiles.active=prod", "/app.jar"]
