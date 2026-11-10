# load-test

실제 Riot API 로 갱신 부하를 주는 k6 스크립트와 데이터셋. 인프라는 `docker/infra`, 지표 화면은 `docker/monitoring` 에 있다.

| 파일 | 내용 |
|---|---|
| `renewal-steady.js` | 초당 `RATE`(기본 1)명을 `DURATION`(기본 5m) 동안 갱신. 갱신 요청 → 완료 응답이 한 사이클, 3초 이내 SUCCESS 면 성공 |
| `renewal.js` | Riot ID 몇 개로 조회 → 갱신 → 매치 목록을 한 번씩 확인 |
| `run.sh` | k6 를 돌리며 지표를 모니터링 Prometheus 로 보낸다 (실행마다 `testid` 태그) |
| `data.js` | 갱신할 소환사 목록. git 에 넣지 않는다 (아래 "데이터셋") |

## 실행

```bash
# 1. 인프라 (postgres·rabbitmq·redis)
docker compose -f docker/infra/compose.yml up -d --wait
# 2. 앱 — 호스트에서 local 프로파일, 실제 RIOT_API_KEY. 대시보드의 서버 갱신 시간 분위수는
#    --management.metrics.distribution.percentiles-histogram.lol.renewal.duration=true 로 띄워야 나온다
# 3. 모니터링 (Prometheus·Grafana)
docker compose -f docker/monitoring/compose.yml up -d
# 4. 부하
bash load-test/run.sh renewal-steady.js
RATE=2 DURATION=3m bash load-test/run.sh renewal-steady.js
```

Grafana: http://localhost:13000 (로그인 없음), 대시보드 "갱신 부하 테스트". 위의 "k6 실행" 목록에서 `testid` 를 골라 실행끼리 나눠 본다. 포트는 `PROMETHEUS_PORT`·`GRAFANA_PORT` 로 바꾼다 (`run.sh` 도 `PROMETHEUS_PORT` 를 본다).

## 데이터셋 (`data.js`)

`renewal-steady.js` 는 기본으로 `load-test/data.js` 를 읽는다. 다른 파일은 `-e DATA=/절대/경로` 로 준다. 실제 소환사 puuid 라 git 에 넣지 않는다 (`.gitignore`).

파일에서 첫 `{` 부터 마지막 `}` 까지를 JSON 으로 읽고 `entries[].puuid` 만 쓴다. 그래서 그 사이는 올바른 JSON 이어야 한다 (키에 따옴표). Riot league API(챌린저 리그 등) 응답을 그대로 붙여 넣어도 된다.

```js
const userInfo = {
  "tier": "CHALLENGER",
  "entries": [
    { "puuid": "..." },
    { "puuid": "..." }
  ]
}
```

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

- setup 이 데이터셋의 소환사를 DB 에서 찾고, 없으면 Riot 에서 등록한다. 등록도 안 되는 소환사는 뺀다.
- 처음 갱신하는 소환사는 최근 20경기를 받고, 뒤이어 추가 매치 찾기가 시즌 매치를 수백 건 받는다. 두 번째 갱신부터는 새 매치만 받아 훨씬 빠르다. 첫 갱신과 재갱신을 섞지 않으려면 같은 소환사로 바로 다시 돌리지 않는다.
- 앱이 뜨고 1분 뒤, 그 뒤 2시간마다 랭킹 스케줄러가 상위 티어 미등록 소환사 약 1000명을 Riot 에서 조회한다. 부하 중이면 같은 Riot 한도를 쓰므로, 부하 테스트 때는 앱을 `--lol.ranking.scheduler.enabled=false` 로 띄워 끈다 (관리자 API 수동 실행은 그대로 된다).
