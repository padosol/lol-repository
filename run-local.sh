#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# .env 파일 로드 (있으면)
ENV_FILE="$SCRIPT_DIR/.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

# Git 서브모듈 업데이트 (이 프로젝트는 lol-db-schema 서브모듈 사용)
git submodule update --init --recursive

echo "==> Gradle 빌드..."
if [ "${SKIP_BUILD:-false}" != "true" ]; then
  "$SCRIPT_DIR/gradlew" build -x test --no-daemon
fi

JAR_FILE="$SCRIPT_DIR/app/build/libs/app-0.0.1-SNAPSHOT.jar"
LOL_VT_ENABLED="${LOL_VT_ENABLED:-true}"
LOL_VT_EXECUTORS_ENABLED="${LOL_VT_EXECUTORS_ENABLED:-$LOL_VT_ENABLED}"
JVM_DIAGNOSTIC_OPTS="${JVM_DIAGNOSTIC_OPTS:-}"
MANAGEMENT_ENDPOINTS_EXPOSURE_INCLUDE="${MANAGEMENT_ENDPOINTS_EXPOSURE_INCLUDE:-health,info,metrics,prometheus,threaddump}"

echo "==> 애플리케이션 실행 (local 프로파일, VT=${LOL_VT_ENABLED}, executors=${LOL_VT_EXECUTORS_ENABLED})..."
LOL_VT_ENABLED="$LOL_VT_ENABLED" \
LOL_VT_EXECUTORS_ENABLED="$LOL_VT_EXECUTORS_ENABLED" \
MANAGEMENT_ENDPOINTS_EXPOSURE_INCLUDE="$MANAGEMENT_ENDPOINTS_EXPOSURE_INCLUDE" \
java \
  ${JVM_DIAGNOSTIC_OPTS} \
  -DDB_HOST="${DB_HOST:-localhost}" \
  -DDB_PORT="${DB_PORT:-5432}" \
  -DDB_NAME="${DB_NAME:-postgres}" \
  -DDB_USERNAME="${DB_USERNAME:-postgres}" \
  -DDB_PASSWORD="${DB_PASSWORD:-1234}" \
  -jar "$JAR_FILE" \
  --spring.profiles.active=local
