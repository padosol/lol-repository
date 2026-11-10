# load-test

실제 Riot API 로 갱신 부하를 주고 Grafana 로 본다. 실험실(`docker/lab`, 가짜 Riot)과 달리 실제 키·실제 소환사를 쓴다.

| 파일 | 내용 |
|---|---|
| `renewal-steady.js` | 초당 `RATE`(기본 1)명을 `DURATION`(기본 5m) 동안 갱신. 갱신 요청 → 완료 응답이 한 사이클, 3초 이내 SUCCESS 면 성공 |
| `renewal.js` | Riot ID 몇 개로 조회 → 갱신 → 매치 목록을 한 번씩 확인 |
| `data.js` | 갱신할 소환사 목록 (`entries[].puuid`, 실제 KR 챌린저 300명). 같은 형식의 다른 파일은 `-e DATA=/절대/경로` |
| `run.sh` | k6 를 돌리며 지표를 Prometheus 로 보낸다 (실행마다 `testid` 태그) |
| `monitoring/` | Prometheus(:19090) + Grafana(:13000), 대시보드 "갱신 부하 테스트" |

## 실행

```bash
# 1. 인프라 (postgres·rabbitmq·redis)
docker compose -f docker/lab/compose.yml up -d --wait postgres rabbitmq redis
# 2. 앱 — local 프로파일, 실제 RIOT_API_KEY. 대시보드의 서버 갱신 시간 분위수는
#    --management.metrics.distribution.percentiles-histogram.lol.renewal.duration=true 로 띄워야 나온다
# 3. 모니터링
docker compose -f load-test/monitoring/compose.yml up -d
# 4. 부하
bash load-test/run.sh renewal-steady.js
RATE=2 DURATION=3m bash load-test/run.sh renewal-steady.js
```

Grafana: http://localhost:13000 (로그인 없음). 포트는 `LT_PROMETHEUS_PORT`·`LT_GRAFANA_PORT` 로 바꾼다.

## 사이클 결과 (`renewal_cycle_outcome`)

| outcome | 뜻 | 성공률 |
|---|---|---|
| `success` | 완료 응답 SUCCESS, POST 부터 3초 이내 | 성공 |
| `slow` | SUCCESS 지만 3초 초과 | 실패 |
| `failed` | 완료 응답이 FAILED (갱신 실패·기한 초과) | 실패 |
| `timeout` | 10초(`POLL_CAP_SECONDS`) 넘게 PROGRESS | 실패 |
| `error` | POST 가 202 가 아니거나 응답이 이상함 | 실패 |
| `rejected` | 쿨다운(같은 소환사 2분)으로 거절 | 넣지 않음 |

상태 조회 간격은 0.5초(`POLL_INTERVAL`)라 사이클 시간은 0.5초 단위로 잡힌다. 서버가 잰 시간은 대시보드의 "서버 갱신 시간" 이다.

## 알아 둘 것

- setup 이 `data.js` 의 소환사를 DB 에서 찾고, 없으면 Riot 에서 등록한다. 등록도 안 되는 소환사는 뺀다.
- 처음 갱신하는 소환사는 최근 20경기를 받고, 뒤이어 추가 매치 찾기가 시즌 매치를 수백 건 받는다. 두 번째 갱신부터는 새 매치만 받아 훨씬 빠르다. 첫 갱신과 재갱신을 섞지 않으려면 같은 소환사로 바로 다시 돌리지 않는다.
- 앱이 뜨고 1분 뒤, 그 뒤 2시간마다 랭킹 스케줄러가 상위 티어 미등록 소환사 약 1000명을 Riot 에서 조회한다. 부하 중이면 같은 Riot 한도를 쓰므로, 부하 테스트 때는 앱을 `--lol.ranking.scheduler.enabled=false` 로 띄워 끈다 (관리자 API 수동 실행은 그대로 된다).
