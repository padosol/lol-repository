#!/usr/bin/env bash
# Riot API 실험실 명령 모음. 저장소 루트에서 `bash docker/lab/lab.sh <명령>` 로 쓴다.
# 의존성은 docker 와 curl 뿐이다.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

MOCK_URL="${MOCK_URL:-http://localhost:8099}"
APP_URL="${APP_URL:-http://localhost:8111}"
LAB_SERVICES=(postgres rabbitmq redis prometheus grafana riot-mock redis-exporter)

compose() {
  docker compose -p lol-repository -f docker/docker-compose.yml -f docker/lab/compose.yml "$@"
}

psql_admin() {
  compose exec -T postgres psql -U postgres -d postgres -v ON_ERROR_STOP=1 -tA "$@"
}

wait_infra() {
  local i
  for i in $(seq 1 60); do
    compose exec -T postgres pg_isready -U postgres >/dev/null 2>&1 && break
    sleep 1
  done
  compose exec -T postgres pg_isready -U postgres >/dev/null
  for i in $(seq 1 90); do
    compose exec -T rabbitmq rabbitmq-diagnostics -q check_running >/dev/null 2>&1 && break
    sleep 1
  done
  compose exec -T rabbitmq rabbitmq-diagnostics -q check_running >/dev/null
}

ensure_lab_db() {
  if [ -z "$(psql_admin -c "SELECT 1 FROM pg_database WHERE datname = 'lol_lab'")" ]; then
    psql_admin -c "CREATE DATABASE lol_lab" >/dev/null
    echo "lol_lab 생성"
  fi
}

ensure_vhost() {
  compose exec -T rabbitmq rabbitmqctl add_vhost lab >/dev/null 2>&1 || true
  compose exec -T rabbitmq rabbitmqctl set_permissions -p lab guest ".*" ".*" ".*" >/dev/null
}

cmd_up() {
  ./gradlew :tools:riot-mock:bootJar
  compose up -d --build "${LAB_SERVICES[@]}"
  wait_infra
  ensure_lab_db
  ensure_vhost
  compose exec -T rabbitmq rabbitmq-plugins enable rabbitmq_prometheus >/dev/null
  cmd_urls
}

# 볼륨과 컨테이너를 지우는 `compose down` 은 쓰지 않는다. 멈추기만 한다.
cmd_down() {
  compose stop "${LAB_SERVICES[@]}"
}

cmd_app() {
  exec ./gradlew :module:app:application:bootRun -Dspring.profiles.active=local,lab
}

cmd_urls() {
  echo "Grafana     http://localhost:3000"
  echo "Prometheus  http://localhost:9090"
  echo "RabbitMQ    http://localhost:15672 (guest/guest, vhost lab)"
  echo "riot-mock   ${MOCK_URL}"
}

require_lab_app() {
  if ! curl -s "${APP_URL}/actuator/info" | grep -q '"lab":true'; then
    echo "앱이 lab 프로파일로 떠 있지 않습니다"
    exit 1
  fi
}

mock_put_limits() {
  curl -sf -X PUT -H 'Content-Type: application/json' -d "$1" "${MOCK_URL}/admin/limits"
}

# 인자가 있으면 그 epoch 로, 없으면 +1.
mock_reset() {
  if [ $# -ge 1 ]; then
    curl -sf -X POST -H 'Content-Type: application/json' -d "$(printf '{"epoch":%s}' "$1")" "${MOCK_URL}/admin/reset"
  else
    curl -sf -X POST "${MOCK_URL}/admin/reset"
  fi
}

cmd_limit() {
  if [ $# -ne 1 ]; then
    echo "사용법: lab.sh limit <한도>  (예: 500:10)"
    exit 1
  fi
  mock_put_limits "$(printf '{"app":"%s"}' "$1")"
  echo
}

cmd_reset() {
  psql_admin -c "DROP DATABASE IF EXISTS lol_lab WITH (FORCE)" >/dev/null
  psql_admin -c "DROP DATABASE IF EXISTS lol_lab_snapshot WITH (FORCE)" >/dev/null
  psql_admin -c "CREATE DATABASE lol_lab" >/dev/null
  compose exec -T redis redis-cli -n 1 FLUSHDB
  compose exec -T rabbitmq rabbitmqctl delete_vhost lab >/dev/null 2>&1 || true
  ensure_vhost
  mock_reset 0
  echo
}

main() {
  local cmd="${1:-}"
  [ $# -gt 0 ] && shift
  case "$cmd" in
    up) cmd_up ;;
    down) cmd_down ;;
    app) cmd_app ;;
    urls) cmd_urls ;;
    limit) cmd_limit "$@" ;;
    reset) cmd_reset ;;
    *)
      echo "사용법: lab.sh {up|down|app|urls|limit <한도>|reset}"
      exit 1
      ;;
  esac
}

# source 하면 함수만 쓸 수 있고, 직접 실행할 때만 명령을 돈다.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
