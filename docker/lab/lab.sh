#!/usr/bin/env bash
# Riot API 실험실 명령 모음. 저장소 루트에서 `bash docker/lab/lab.sh <명령>` 로 쓴다.
# 의존성은 docker 와 curl 뿐이다.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

MOCK_URL="${MOCK_URL:-http://localhost:8099}"
APP_URL="${APP_URL:-http://localhost:8111}"
LAB_SERVICES=(postgres rabbitmq redis prometheus grafana riot-mock redis-exporter)
DRAIN_TIMEOUT_SECONDS=1800
# seed 동안만 가짜 서버 한도를 올린다. account-v1 의 1000:60 이면 Riot ID 조회 4000건이 초당 16건으로 묶인다.
SEED_LIMITS='{"app":"100000:10","methods":{"account-v1.by-riot-id":"100000:10","account-v1.by-puuid":"100000:10",'\
'"summoner-v4.by-puuid":"100000:10","league-v4.entries-by-puuid":"100000:10","match-v5.ids-by-puuid":"100000:10",'\
'"match-v5.match":"100000:10","match-v5.timeline":"100000:10"}}'
SEED_SAVED_LIMITS=""

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

# 가짜 서버 조작 API 를 부르고 응답 본문을 출력한다. 실패하면 curl 오류나 응답 본문을 보이고 1 을 낸다.
mock_admin() {
  local method="$1" path="$2" out
  if [ $# -ge 3 ]; then
    out="$(curl -sS --fail-with-body -X "$method" -H 'Content-Type: application/json' -d "$3" "${MOCK_URL}${path}")" || {
      echo "가짜 서버 조작 실패: ${method} ${path} ${out}" >&2
      return 1
    }
  else
    out="$(curl -sS --fail-with-body -X "$method" "${MOCK_URL}${path}")" || {
      echo "가짜 서버 조작 실패: ${method} ${path} ${out}" >&2
      return 1
    }
  fi
  printf '%s' "$out"
}

mock_get_limits() {
  mock_admin GET /admin/limits
}

mock_put_limits() {
  mock_admin PUT /admin/limits "$1"
}

# 인자가 있으면 그 epoch 로, 없으면 +1.
mock_reset() {
  if [ $# -ge 1 ]; then
    mock_admin POST /admin/reset "$(printf '{"epoch":%s}' "$1")"
  else
    mock_admin POST /admin/reset
  fi
}

# k6 를 compose 의 k6 컨테이너로 돌린다. 스크립트는 docker/lab/k6 이고 컨테이너의 /scripts 다.
k6_run() {
  compose --progress quiet run --rm k6 run "$@"
}

# 부하 스크립트. 지표를 Prometheus 로 remote write 한다 (주소·전송 주기는 compose 의 K6_PROMETHEUS_RW_*).
# 셸의 RATE·DURATION 은 k6 __ENV 로 넘긴다. 비어 있으면 스크립트 기본값을 쓴다.
k6_load() {
  local script="$1"
  shift
  k6_run -o experimental-prometheus-rw -e "RATE=${RATE:-}" -e "DURATION=${DURATION:-}" "$@" "/scripts/${script}.js"
}

require_scenario() {
  if ! [[ "${1:-}" =~ ^[a-z][a-z0-9-]*$ ]] || [ "$1" = seed ] || [ "$1" = verify ] || [ ! -f "docker/lab/k6/$1.js" ]; then
    echo "알 수 없는 시나리오: ${1:-} (예: steady)"
    exit 1
  fi
}

# consumer 가 있는 큐의 메시지(대기+처리 중) 합. consumer 가 없는 큐(이 브랜치의 renewal.match.find.queue 등)는 비지 않으므로 뺀다.
# $(...) 안에서 불리므로 set -e 에 기대지 않고 실패를 직접 돌려준다.
lab_queue_pending() {
  local rows
  rows="$(compose exec -T rabbitmq rabbitmqctl list_queues -p lab messages consumers -q --no-table-headers)" || return 1
  printf '%s\n' "$rows" | awk '$2 > 0 { sum += $1 } END { print sum + 0 }'
}

# lab_queue_pending 이 10초 연속 0 이 될 때까지 기다린다. 인자: 최대 초.
wait_lab_queues_drained() {
  local timeout="$1" deadline now pending zero_since="" last_report=0
  deadline=$(( $(date +%s) + timeout ))
  while :; do
    pending="$(lab_queue_pending)"
    now=$(date +%s)
    if [ "$pending" -eq 0 ]; then
      [ -n "$zero_since" ] || zero_since=$now
      [ $(( now - zero_since )) -lt 10 ] || return 0
    else
      zero_since=""
    fi
    if [ "$now" -ge "$deadline" ]; then
      echo "vhost lab 큐가 ${timeout}초 안에 비지 않았습니다 (남은 메시지 ${pending})" >&2
      return 1
    fi
    if [ $(( now - last_report )) -ge 30 ]; then
      echo "  큐 남은 메시지 ${pending}"
      last_report=$now
    fi
    sleep 1
  done
}

purge_lab_queues() {
  local queues q
  queues="$(compose exec -T rabbitmq rabbitmqctl list_queues -p lab name -q --no-table-headers)"
  for q in $queues; do
    compose exec -T rabbitmq rabbitmqctl purge_queue -p lab "$q" >/dev/null
  done
}

allow_lab_connections() {
  psql_admin -c "ALTER DATABASE lol_lab ALLOW_CONNECTIONS true" >/dev/null
}

# 앱이 붙어 있어도 되도록 새 연결을 막고 끊은 뒤 템플릿으로 복사한다. 실패해도 연결 허용은 되돌린다.
snapshot_lab_db() {
  psql_admin -c "ALTER DATABASE lol_lab ALLOW_CONNECTIONS false" >/dev/null
  trap allow_lab_connections EXIT
  psql_admin -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'lol_lab' AND pid <> pg_backend_pid()" >/dev/null
  psql_admin -c "DROP DATABASE IF EXISTS lol_lab_snapshot WITH (FORCE)" >/dev/null
  psql_admin -c "CREATE DATABASE lol_lab_snapshot TEMPLATE lol_lab" >/dev/null
  allow_lab_connections
  trap - EXIT
}

restore_lab_db() {
  psql_admin -c "DROP DATABASE IF EXISTS lol_lab WITH (FORCE)" >/dev/null
  psql_admin -c "CREATE DATABASE lol_lab TEMPLATE lol_lab_snapshot" >/dev/null
}

# 스냅숏 복원 뒤 앱 커넥션 풀이 새 lol_lab 에 다시 붙었는지 본다.
wait_app_healthy() {
  local i
  for i in $(seq 1 30); do
    if curl -s "${APP_URL}/actuator/health" | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 1
  done
  echo "앱 health 가 30초 안에 UP 이 되지 않았습니다: $(curl -s "${APP_URL}/actuator/health")" >&2
  return 1
}

restore_seed_limits() {
  if ! mock_put_limits "$SEED_SAVED_LIMITS" >/dev/null; then
    echo "가짜 서버 한도를 되돌리지 못했습니다. seed 전 값: ${SEED_SAVED_LIMITS}" >&2
    return 1
  fi
}

cmd_seed() {
  local started step summoners
  require_lab_app
  started=$(date +%s)
  SEED_SAVED_LIMITS="$(mock_get_limits)"
  mock_put_limits "$SEED_LIMITS" >/dev/null
  trap restore_seed_limits EXIT
  mock_reset 0 >/dev/null

  step=$(date +%s)
  k6_load seed -e PHASE=create
  echo "create $(( $(date +%s) - step ))초"
  step=$(date +%s)
  k6_load seed -e PHASE=warm
  echo "warm 요청 $(( $(date +%s) - step ))초"
  step=$(date +%s)
  wait_lab_queues_drained "$DRAIN_TIMEOUT_SECONDS"
  echo "첫 갱신 처리 $(( $(date +%s) - step ))초"

  trap - EXIT
  restore_seed_limits
  summoners="$(compose exec -T postgres psql -U postgres -d lol_lab -v ON_ERROR_STOP=1 -tA -c "SELECT count(*) FROM riot.summoner")"
  step=$(date +%s)
  snapshot_lab_db
  echo "스냅숏 $(( $(date +%s) - step ))초"
  echo "seed 완료: 소환사 ${summoners}명, lol_lab_snapshot 생성, 합계 $(( $(date +%s) - started ))초"
}

cmd_load() {
  require_scenario "${1:-}"
  k6_load "$1"
}

run_verify() {
  local scenario="$1" start="$2" end="$3" rc=0
  echo "판정 ${scenario}  START=${start}  END=${end}  ($(( end - start ))초)"
  k6_run -q --log-format raw -e "SCENARIO=${scenario}" -e "START=${start}" -e "END=${end}" /scripts/verify.js || rc=$?
  if [ "$rc" -eq 99 ]; then
    echo "FAIL"
    return 1
  fi
  if [ "$rc" -ne 0 ]; then
    echo "판정을 끝내지 못했습니다 (k6 종료 코드 ${rc})" >&2
    return "$rc"
  fi
}

cmd_verify() {
  if [ $# -ne 3 ]; then
    echo "사용법: lab.sh verify <시나리오> <START> <END>  (unix 초)"
    exit 1
  fi
  require_scenario "$1"
  run_verify "$@"
}

cmd_run() {
  local scenario="${1:-}" snapshot epoch start end
  require_scenario "$scenario"
  require_lab_app
  snapshot="$(psql_admin -c "SELECT 1 FROM pg_database WHERE datname = 'lol_lab_snapshot'")"
  if [ -z "$snapshot" ]; then
    echo "lol_lab_snapshot 이 없습니다. 먼저 seed 를 돌리세요"
    exit 1
  fi
  # 지난 실행이 남긴 메시지를 비우고 처리 중인 갱신이 끝나길 기다린 뒤(그동안 새로 쌓인 것도 비운다) 시작 상태를 되돌린다
  purge_lab_queues
  wait_lab_queues_drained 300
  purge_lab_queues
  restore_lab_db
  compose exec -T redis redis-cli -n 1 FLUSHDB >/dev/null
  wait_app_healthy
  epoch="$(mock_reset)"
  echo "시작 상태 복원: lol_lab ← lol_lab_snapshot, Redis DB 1 비움, vhost lab 비움, 가짜 서버 ${epoch}"

  start=$(date +%s)
  k6_load "$scenario"
  echo "부하 끝. 60초 뒤 판정한다"
  sleep 60
  end=$(date +%s)
  run_verify "$scenario" "$start" "$end"
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
    seed) cmd_seed ;;
    load) cmd_load "$@" ;;
    run) cmd_run "$@" ;;
    verify) cmd_verify "$@" ;;
    *)
      echo "사용법: lab.sh {up|down|app|urls|limit <한도>|reset|seed|load <시나리오>|run <시나리오>|verify <시나리오> <START> <END>}"
      exit 1
      ;;
  esac
}

# source 하면 함수만 쓸 수 있고, 직접 실행할 때만 명령을 돈다.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
