#!/usr/bin/env bash
# 부하 테스트 전에 로컬 데이터를 "갱신 안 된 소환사" 상태로 되돌린다 (docker/infra 컨테이너 대상).
#
#   bash load-test/reset-db.sh             # 지울 양을 보여 주고, yes 를 입력하면 초기화
#   bash load-test/reset-db.sh --dry-run   # 보여 주기만 한다
#   bash load-test/reset-db.sh --yes       # 확인 없이 초기화
#
# 하는 일
#   - summoner 는 남기고 갱신 일시(revision_date·last_riot_call_date)를 2026-01-01 로 되돌린다 (앱이 소환사를 처음 등록할 때 넣는 값)
#   - 리그·랭킹·티어 커트라인 테이블과 매치·타임라인·이벤트 테이블을 비운다
#   - Redis db 0 을 비운다 (Riot 한도 상태, 매치 처리 중복 키 등)
#   - RabbitMQ vhost / 의 큐 메시지를 비운다 (큐는 남긴다)
# 건드리지 않는 것: summoner 행, item_meta(Flyway V1 기준 데이터), 게임 데이터(patch_*·queue_data·season), esports_*, batch_*, flyway_schema_history
#
# 앱이 떠 있으면 멈춘다. 배경 적재가 지우는 사이에 다시 쓰므로 앱을 먼저 끈다.
set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-lol-postgres}"
PG_DB="${RESET_DB_NAME:-lol_local}"
REDIS_CONTAINER="${REDIS_CONTAINER:-lol-redis}"
REDIS_DB="${RESET_REDIS_DB:-0}"
RABBITMQ_API="${RABBITMQ_API:-http://localhost:15672/api}"
RABBITMQ_VHOST="${RESET_RABBITMQ_VHOST:-/}"
APP_URL="${APP_URL:-http://localhost:8111}"
RESET_DATE="2026-01-01 00:00:00"

LEAGUE_TABLES=(league league_summoner league_summoner_history
  summoner_ranking summoner_ranking_backup tier_cutoff tier_cutoff_backup)
MATCH_TABLES=(match match_ban match_participant match_participant_challenges match_team
  participant_frame timeline_event_frame
  building_events champion_special_kill_event game_end_event item_event kill_event
  level_up_event skill_level_up_event turret_plate_destroyed_event ward_event)
TABLES=("${LEAGUE_TABLES[@]}" "${MATCH_TABLES[@]}")

MODE=confirm
case "${1:-}" in
  "") ;;
  --dry-run) MODE=dry-run ;;
  -y|--yes) MODE=yes ;;
  -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) echo "알 수 없는 옵션: $1 (--dry-run, --yes, --help)" >&2; exit 2 ;;
esac

pg() { docker exec -i "$PG_CONTAINER" psql -U postgres -d "$PG_DB" -v ON_ERROR_STOP=1 -qtA -F' ' "$@"; }
sql_list() { local IFS=,; echo "$*"; }
sql_array() { printf "'%s'," "$@" | sed 's/,$//'; }

# RabbitMQ 관리 API: queues → "이름 메시지수 consumer수" 줄, purge → 큐마다 메시지 비우기
mq() {
  RABBITMQ_API="$RABBITMQ_API" RABBITMQ_VHOST="$RABBITMQ_VHOST" \
  RABBITMQ_USER="${RABBITMQ_USERNAME:-guest}" RABBITMQ_PASS="${RABBITMQ_PASSWORD:-guest}" \
  python3 -I - "$1" <<'PY'
import base64, json, os, sys, urllib.parse, urllib.request
api, vhost = os.environ["RABBITMQ_API"], urllib.parse.quote(os.environ["RABBITMQ_VHOST"], safe="")
auth = base64.b64encode(f'{os.environ["RABBITMQ_USER"]}:{os.environ["RABBITMQ_PASS"]}'.encode()).decode()
def call(path, method="GET"):
    req = urllib.request.Request(api + path, method=method, headers={"Authorization": "Basic " + auth})
    with urllib.request.urlopen(req, timeout=10) as res:
        body = res.read()
    return json.loads(body) if body else None
queues = call(f"/queues/{vhost}?columns=name,messages,consumers")
if sys.argv[1] == "queues":
    for q in queues:
        print(q["name"], q.get("messages") or 0, q.get("consumers") or 0)
elif sys.argv[1] == "purge":
    for q in queues:
        call(f"/queues/{vhost}/{urllib.parse.quote(q['name'], safe='')}/contents", "DELETE")
    print(f"큐 {len(queues)}개 메시지 비움")
PY
}

show_state() {
  pg -c "SET search_path = riot" -c "
    SELECT '  소환사 ' || count(*) || '명 (갱신 일시가 ${RESET_DATE%% *} 가 아닌 소환사 '
           || count(*) FILTER (WHERE revision_date IS DISTINCT FROM TIMESTAMP '${RESET_DATE}'
                                  OR last_riot_call_date IS DISTINCT FROM TIMESTAMP '${RESET_DATE}') || '명)'
    FROM summoner"
  pg -c "SET search_path = riot" -c "
    SELECT '  매치 ' || (SELECT count(*) FROM match) || '건, 리그 소환사 ' || (SELECT count(*) FROM league_summoner)
           || '명, 비울 테이블 행 합계 약 ' || coalesce(sum(greatest(c.reltuples, 0))::bigint, 0) || '행'
    FROM pg_class c WHERE c.oid IN (SELECT to_regclass(t) FROM unnest(ARRAY[$(sql_array "${TABLES[@]}")]) AS t)"
  echo "  Redis db ${REDIS_DB} 키 $(docker exec "$REDIS_CONTAINER" redis-cli -n "$REDIS_DB" DBSIZE)개"
  local total=0 name messages consumers
  while read -r name messages consumers; do total=$((total + messages)); done < <(mq queues)
  echo "  RabbitMQ vhost ${RABBITMQ_VHOST} 큐 메시지 ${total}건"
}

# 1. 앱이 떠 있으면 멈춘다 (dry-run 은 경고만)
app_running=""
if curl -fs -m 2 -o /dev/null "${APP_URL}/actuator/health"; then
  app_running="앱이 ${APP_URL} 에 떠 있습니다"
else
  consumers=0
  while read -r _ _ c; do consumers=$((consumers + c)); done < <(mq queues)
  if [ "$consumers" -gt 0 ]; then app_running="RabbitMQ vhost ${RABBITMQ_VHOST} 에 consumer ${consumers}개가 붙어 있습니다 (다른 곳에 앱이 떠 있음)"; fi
fi
if [ -n "$app_running" ]; then
  if [ "$MODE" != dry-run ]; then echo "${app_running}. 앱을 먼저 끄고 다시 실행하세요." >&2; exit 1; fi
  echo "주의: ${app_running}. 실제 초기화 때는 앱을 먼저 꺼야 합니다."
fi

# 2. 비울 테이블이 다 있는지 확인한다 (스키마가 바뀌었으면 멈춘다)
missing="$(pg -c "SET search_path = riot" -c "SELECT string_agg(t, ' ') FROM unnest(ARRAY[$(sql_array "${TABLES[@]}")]) AS t WHERE to_regclass(t) IS NULL")"
if [ -n "$missing" ]; then echo "${PG_DB}.riot 에 없는 테이블: ${missing}. 스크립트의 테이블 목록을 고치세요." >&2; exit 1; fi

echo "초기화 대상 (${PG_CONTAINER}/${PG_DB}, ${REDIS_CONTAINER} db ${REDIS_DB}, RabbitMQ ${RABBITMQ_VHOST})"
show_state

if [ "$MODE" = dry-run ]; then echo "dry-run 이라 아무것도 바꾸지 않았습니다."; exit 0; fi
if [ "$MODE" = confirm ]; then
  read -r -p "위 데이터를 지웁니다. 되돌릴 수 없습니다. 계속하려면 yes 를 입력하세요: " answer
  [ "$answer" = yes ] || { echo "취소했습니다."; exit 1; }
fi

# 3. DB: 소환사 갱신 일시 되돌리기 + 리그·매치 테이블 비우기 (한 트랜잭션)
pg <<SQL
SET search_path = riot;
BEGIN;
UPDATE summoner SET revision_date = TIMESTAMP '${RESET_DATE}', last_riot_call_date = TIMESTAMP '${RESET_DATE}';
TRUNCATE $(sql_list "${LEAGUE_TABLES[@]}");
TRUNCATE $(sql_list "${MATCH_TABLES[@]}");
COMMIT;
SQL
echo "DB 초기화 완료"

# 4. Redis · RabbitMQ
docker exec "$REDIS_CONTAINER" redis-cli -n "$REDIS_DB" FLUSHDB > /dev/null
echo "Redis db ${REDIS_DB} 비움"
mq purge

echo "초기화 뒤"
show_state
