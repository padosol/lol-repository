#!/usr/bin/env bash
# Riot API 실험실 명령 모음. 저장소 루트에서 `bash docker/lab/lab.sh <명령>` 로 쓴다 (source 하지 않는다).
# 의존성은 docker 와 curl 뿐이다.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

MOCK_URL="${MOCK_URL:-http://localhost:8099}"
# 호스트에서 본 앱 주소. k6 컨테이너에는 k6_app_url 이 바꾼 주소로 넘긴다 (Prometheus 수집 대상은 prometheus.yml 의 8111 고정).
APP_URL="${APP_URL:-http://localhost:8111}"
PROM_URL="${PROM_URL:-http://localhost:9090}"
LAB_SERVICES=(postgres rabbitmq redis prometheus grafana riot-mock redis-exporter)
DRAIN_TIMEOUT_SECONDS=1800
# 갱신 처리를 기다리고 적체를 판정할 vhost lab 큐. 이 브랜치에서 consumer 가 있게 설계된 큐를 이름으로 고른다.
# consumer 수로 고르면 리스너가 죽었을 때 적체가 숨는다. PR #130 이 들어와 추가 매치 검색 리스너가 켜지면
# renewal.match.find.queue 를 더한다 (LAB_QUEUES="mmrtr.summoner renewal.match.find.queue"). verify.js 도 이 값을 받는다.
LAB_QUEUES="${LAB_QUEUES:-mmrtr.summoner}"
# seed 가 만드는 소환사 수와 첫 갱신을 보내는 재방문 풀 크기 (k6/lib/lab.js 의 FIRST·RETURNING: 1..4000, 2501..4000)
SEED_PLAYERS=4000
SEED_RETURNING_COUNT=1500
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

psql_lab() {
  compose exec -T postgres psql -U postgres -d lol_lab -v ON_ERROR_STOP=1 -tA "$@"
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
  echo "Prometheus  ${PROM_URL}"
  echo "RabbitMQ    http://localhost:15672 (guest/guest, vhost lab)"
  echo "riot-mock   ${MOCK_URL}"
}

require_lab_app() {
  if ! curl -s "${APP_URL}/actuator/info" | grep -q '"lab":true'; then
    echo "앱이 lab 프로파일로 떠 있지 않습니다 (${APP_URL})"
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

# APP_URL 을 k6 컨테이너에서 닿는 주소로 바꾼다 (localhost·127.0.0.1 → host.docker.internal).
k6_app_url() {
  printf '%s' "$APP_URL" | sed -E 's#^(https?://)(localhost|127\.0\.0\.1)([:/]|$)#\1host.docker.internal\3#'
}

# k6 를 compose 의 k6 컨테이너로 돌린다. 스크립트는 docker/lab/k6 이고 컨테이너의 /scripts 다.
# 앱 주소와 판정할 큐 이름은 이 스크립트의 값을 넘긴다 (k6 스크립트에 따로 두지 않는다).
k6_run() {
  compose --progress quiet run --rm k6 run -e "APP_URL=$(k6_app_url)" -e "LAB_QUEUES=${LAB_QUEUES}" "$@"
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

# LAB_QUEUES 의 메시지(대기+처리 중) 합과 consumer 합을 "<메시지> <consumer>" 로 찍는다.
# 큐가 하나라도 없으면(reset 뒤 앱을 다시 띄우지 않았을 때) 0 으로 넘기지 않고 실패한다.
# $(...) 안에서 불리므로 set -e 에 기대지 않고 실패를 직접 돌려준다.
lab_queue_pending() {
  local rows
  rows="$(compose exec -T rabbitmq rabbitmqctl list_queues -p lab name messages consumers -q --no-table-headers)" || return 1
  printf '%s\n' "$rows" | awk -v names="$LAB_QUEUES" '
    BEGIN { n = split(names, want, " "); for (i = 1; i <= n; i++) need[want[i]] = 1 }
    ($1 in need) { msgs += $2; consumers += $3; found[$1] = 1 }
    END {
      for (q in need) {
        if (!(q in found)) {
          print "vhost lab 에 큐 " q " 가 없습니다 (reset 뒤 앱을 다시 띄웠는지 보세요)" > "/dev/stderr"
          exit 1
        }
      }
      print msgs + 0, consumers + 0
    }'
}

# LAB_QUEUES 의 메시지가 10초 연속 0 이 될 때까지 기다린다. 인자: 최대 초.
# consumer 가 0 이면(리스너가 죽었으면) 메시지가 줄지 않아 시간 초과로 실패한다. 진행 줄에 consumer 수를 같이 찍는다.
wait_lab_queues_drained() {
  local timeout="$1" deadline now state pending consumers zero_since="" last_report=0
  deadline=$(( $(date +%s) + timeout ))
  while :; do
    state="$(lab_queue_pending)" || return 1
    pending="${state%% *}"
    consumers="${state##* }"
    now=$(date +%s)
    if [ "$pending" -eq 0 ]; then
      [ -n "$zero_since" ] || zero_since=$now
      [ $(( now - zero_since )) -lt 10 ] || return 0
    else
      zero_since=""
    fi
    if [ "$now" -ge "$deadline" ]; then
      echo "vhost lab 큐(${LAB_QUEUES})가 ${timeout}초 안에 비지 않았습니다 (남은 메시지 ${pending}, consumer ${consumers})" >&2
      return 1
    fi
    if [ $(( now - last_report )) -ge 30 ]; then
      echo "  큐 남은 메시지 ${pending} (consumer ${consumers})"
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

# seed 는 빈 lol_lab 에서만 돈다. 이미 돌린 lol_lab 에 다시 돌리면 매치가 있는 첫 방문 소환사가 스냅숏에 들어간다.
require_empty_lab_db() {
  local count
  if [ "$(psql_lab -c "SELECT to_regclass('riot.summoner') IS NOT NULL")" != t ]; then
    echo "lol_lab 에 riot.summoner 가 없습니다. reset 뒤에는 앱을 다시 띄워야 Flyway 가 테이블을 만듭니다 (bash docker/lab/lab.sh app)" >&2
    exit 1
  fi
  count="$(psql_lab -c "SELECT count(*) FROM riot.summoner")"
  if [ "$count" -ne 0 ]; then
    echo "lol_lab 이 비어 있지 않습니다 (riot.summoner ${count}명). 먼저 bash docker/lab/lab.sh reset 을 하고 앱을 다시 띄우세요" >&2
    exit 1
  fi
}

# Prometheus 즉시 질의 결과의 첫 값. 결과가 비면 빈 문자열이다. 인자: 질의, 평가 시각(unix 초).
prom_value() {
  local body
  body="$(curl -sS --fail-with-body -G "${PROM_URL}/api/v1/query" --data-urlencode "query=$1" --data-urlencode "time=$2")" || {
    echo "Prometheus 질의 실패: $1 ${body}" >&2
    return 1
  }
  printf '%s' "$body" | sed -n 's/.*"value":\[[^,]*,"\([^"]*\)"\].*/\1/p'
}

# 카운터의 [평가 시각 − 구간, 평가 시각] 증가량 질의. 구간 시작에 없던 시리즈는 끝 값을 그대로 더한다 (verify.js 의 delta 와 같다).
prom_delta() {
  printf 'sum((%s - %s offset %s) or (%s unless %s offset %s))' "$1" "$1" "$2" "$1" "$1" "$2"
}

# 스냅숏을 뜨기 전에 시작 상태가 완전한지 본다: 소환사 SEED_PLAYERS 명, warm 구간의 앱 갱신 결과가
# success 정확히 SEED_RETURNING_COUNT 건이고 그 밖(failed·skipped)은 0 건. 아니면 1 을 낸다. 인자: warm 시작, 끝(unix 초).
check_seed_outcome() {
  local from="$1" to="$2" range summoners success others
  range="$(( to - from ))s"
  summoners="$(psql_lab -c "SELECT count(*) FROM riot.summoner")"
  success="$(prom_value "$(prom_delta 'lol_renewal_completed_total{result="success"}' "$range")" "$to")" || return 1
  others="$(prom_value "$(prom_delta 'lol_renewal_completed_total{result!="success"}' "$range")" "$to")" || return 1
  success="${success:-0}"
  others="${others:-0}"
  success="${success%.*}"
  others="${others%.*}"
  echo "  결과 확인: 소환사 ${summoners}명 (기대 ${SEED_PLAYERS}), 첫 갱신 success ${success}건 (기대 ${SEED_RETURNING_COUNT}), failed·skipped ${others}건 (기대 0)"
  if [ "$summoners" -ne "$SEED_PLAYERS" ] || [ "$success" -ne "$SEED_RETURNING_COUNT" ] || [ "$others" -ne 0 ]; then
    echo "시작 상태가 완전하지 않아 스냅숏을 뜨지 않습니다. 앱 로그를 보고 reset → 앱 재기동 → seed 를 다시 하세요" >&2
    return 1
  fi
}

cmd_seed() {
  local started step warm_from summoners
  require_lab_app
  require_empty_lab_db
  started=$(date +%s)
  SEED_SAVED_LIMITS="$(mock_get_limits)"
  mock_put_limits "$SEED_LIMITS" >/dev/null
  # 여기부터는 어디서 끝나든 한도를 되돌린다. 스냅숏 전에 멈추면 lol_lab 연결 허용은 건드리지 않은 상태다
  trap restore_seed_limits EXIT
  mock_reset 0 >/dev/null

  step=$(date +%s)
  k6_load seed -e PHASE=create
  echo "create $(( $(date +%s) - step ))초"
  warm_from=$(date +%s)
  k6_load seed -e PHASE=warm
  echo "warm 요청 $(( $(date +%s) - warm_from ))초"
  step=$(date +%s)
  wait_lab_queues_drained "$DRAIN_TIMEOUT_SECONDS"
  echo "첫 갱신 처리 $(( $(date +%s) - step ))초"
  check_seed_outcome "$warm_from" "$(date +%s)"

  trap - EXIT
  restore_seed_limits
  summoners="$(psql_lab -c "SELECT count(*) FROM riot.summoner")"
  step=$(date +%s)
  snapshot_lab_db
  echo "스냅숏 $(( $(date +%s) - step ))초"
  echo "seed 완료: 소환사 ${summoners}명, 첫 갱신 ${SEED_RETURNING_COUNT}건 성공, lol_lab_snapshot 생성, 합계 $(( $(date +%s) - started ))초"
}

cmd_load() {
  require_scenario "${1:-}"
  require_lab_app
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
  local scenario="${1:-}" snapshot epoch app_limit start end
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
  # reset 전 게이지(지난 실행의 슬라이딩 피크 등) 표본이 창 안에 들지 않도록 새 값이 수집될 때까지 기다린다.
  # 게이지 갱신 1초 + 수집 1초에 여유를 둔다. 기다리지 않으면 START 뒤 2초 동안 지난 피크가 보였다
  sleep 5
  app_limit="$(mock_get_limits | sed -n 's/.*"app":"\([^"]*\)".*/\1/p')"
  echo "시작 상태 복원: lol_lab ← lol_lab_snapshot, Redis DB 1 비움, vhost lab 비움, 가짜 서버 ${epoch}, application 한도 ${app_limit}"

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

main "$@"
