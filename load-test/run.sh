#!/usr/bin/env bash
# load-test 의 k6 스크립트를 돌리며 지표를 모니터링 Prometheus 로 보낸다 (Grafana 대시보드에서 본다).
#
#   bash load-test/run.sh renewal-steady.js
#   RATE=2 DURATION=3m bash load-test/run.sh renewal-steady.js
#
# 실행마다 testid 태그(시각)를 붙여 대시보드에서 실행끼리 골라 볼 수 있다.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="${1:?스크립트 이름을 주세요 (예: renewal-steady.js)}"
shift
PROM_PORT="${PROMETHEUS_PORT:-19090}"

if ! curl -fs -o /dev/null "http://localhost:${PROM_PORT}/-/ready"; then
  echo "Prometheus(:${PROM_PORT}) 가 떠 있지 않습니다. 먼저: docker compose -f docker/monitoring/compose.yml up -d" >&2
  exit 1
fi

export K6_PROMETHEUS_RW_SERVER_URL="http://localhost:${PROM_PORT}/api/v1/write"
export K6_PROMETHEUS_RW_TREND_STATS="p(50),p(95),p(99),avg,max"
# 기본 5초면 대시보드의 짧은 rate 창에 표본이 모자라다
export K6_PROMETHEUS_RW_PUSH_INTERVAL=1s

TESTID="$(date +%Y%m%d-%H%M%S)"
echo "testid=${TESTID}"
exec k6 run -o experimental-prometheus-rw \
  -e RATE="${RATE:-1}" -e DURATION="${DURATION:-5m}" \
  --tag testid="${TESTID}" "$@" "${DIR}/${SCRIPT}"
