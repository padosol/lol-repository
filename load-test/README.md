# load-test

실제 Riot API 로 갱신 부하를 주는 k6 스크립트와 데이터셋. 인프라는 `docker/infra`, 지표 화면은 `docker/monitoring` 에 있다.

| 파일 | 내용 |
|---|---|
| `renewal-steady.js` | 초당 `RATE`(기본 1)명을 `DURATION`(`run.sh` 기본 5m) 동안 갱신. 대상은 `targets.json`. 갱신 요청 → 완료 응답이 한 사이클, 3초 이내 SUCCESS 면 성공 |
| `renewal.js` | Riot ID 몇 개로 조회 → 갱신 → 매치 목록을 한 번씩 확인 |
| `setup.sh` | 데이터셋 소환사를 DB 에 등록하고 쓸 수 있는 puuid 를 `targets.json` 에 적는다 (아래 "대상 준비") |
| `run.sh` | k6 를 돌리며 지표를 모니터링 Prometheus 로 보낸다 (실행마다 `testid` 태그) |
| `reset-db.sh` | 부하 전에 데이터를 "갱신 안 된 소환사" 상태로 되돌린다 (아래 "초기화") |
| `data.js` | 데이터셋(갱신할 소환사 목록). git 에 넣지 않는다 (아래 "데이터셋") |
| `targets.json` | `setup.sh` 가 쓰는 부하 대상 목록. git 에 넣지 않는다 |

## 실행

```bash
# 1. 인프라 (postgres·rabbitmq·redis)
docker compose -f docker/infra/compose.yml up -d --wait
# 2. 앱 — 루트 .env(.env.example 복사, RIOT_API_KEY 채움)를 bootRun 이 읽는다.
#    랭킹 스케줄러 끄기와 서버 갱신 시간 histogram 도 .env 에 들어 있다
./gradlew :module:app:application:bootRun
# 3. 모니터링 (Prometheus·Grafana)
docker compose -f docker/monitoring/compose.yml up -d
# 4. 대상 준비 — 처음 한 번, 데이터셋을 바꿨을 때, DB 를 통째로 비웠을 때
bash load-test/setup.sh
# 5. 부하
bash load-test/run.sh renewal-steady.js
RATE=2 DURATION=3m bash load-test/run.sh renewal-steady.js
```

Grafana: http://localhost:13000 (로그인 없음), 대시보드 "갱신 부하 테스트". 위의 "k6 실행" 목록에서 `testid` 를 골라 실행끼리 나눠 본다. 포트는 `PROMETHEUS_PORT`·`GRAFANA_PORT` 로 바꾼다 (`run.sh` 도 `PROMETHEUS_PORT` 를 본다).

## 초기화 (`reset-db.sh`)

같은 조건으로 다시 돌리려면 부하 전에 앱을 끄고 초기화한다. 앱이 떠 있으면 스크립트가 멈춘다 (배경 적재가 지우는 사이에 다시 쓰므로).

```bash
bash load-test/reset-db.sh --dry-run   # 지울 양만 본다
bash load-test/reset-db.sh             # yes 를 입력하면 초기화 (--yes 는 확인 없이)
```

| 대상 | 하는 일 |
|---|---|
| `summoner` | 행은 남기고 갱신 일시(`revision_date`·`last_riot_call_date`)를 2026-01-01 로 되돌린다. 앱이 소환사를 처음 등록할 때 넣는 값이라 모두 첫 갱신이 된다 |
| 리그 | `league`, `league_summoner(_history)`, `summoner_ranking(_backup)`, `tier_cutoff(_backup)` 를 비운다 |
| 매치 | `match`·참가자·팀·밴, 타임라인(`participant_frame`·`timeline_event_frame`), 이벤트 테이블을 비운다 |
| Redis db 0 | 비운다 (Riot 한도 상태, 매치 처리 중복 키) |
| RabbitMQ vhost `/` | 큐 메시지를 비운다 (큐는 남긴다) |

`item_meta`(Flyway V1 기준 데이터), 게임 데이터, esports, batch, `flyway_schema_history` 는 건드리지 않는다. 비울 테이블이 스키마에 없으면 아무것도 하지 않고 멈춘다.

## 대상 준비 (`setup.sh`)

갱신은 DB 에 있는 소환사만 받는다. `setup.sh` 가 데이터셋의 소환사를 하나씩 앱에서 찾고(`GET /api/v1/summoners/{puuid}`), 없으면 Riot 에서 puuid 로 등록한다(`GET /api/riot/{platform}/summoners/{puuid}`). 등록까지 된 소환사만 `targets.json` 에 적고, 안 되는 소환사는 상태 코드와 함께 보여 주고 뺀다. 앱이 떠 있어야 한다.

부하 직전에 Riot 을 부르지 않도록 k6 에서 뺀 단계다. `reset-db.sh` 는 소환사를 남기므로 초기화 뒤에는 다시 돌리지 않아도 된다.

```bash
bash load-test/setup.sh                            # data.js → targets.json
DATA=/절대/경로/other.js bash load-test/setup.sh   # 다른 데이터셋
```

`renewal-steady.js` 는 `targets.json` 만 읽는다. 다른 목록은 `-e TARGETS=/절대/경로` 로 준다 (`bash load-test/run.sh renewal-steady.js -e TARGETS=...`).

## 데이터셋 (`data.js`)

`setup.sh` 가 기본으로 `load-test/data.js` 를 읽는다. 다른 파일은 `DATA=/절대/경로` 로 준다. 실제 소환사 puuid 라 git 에 넣지 않는다 (`.gitignore`).

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

- 처음 갱신하는 소환사는 최근 20경기를 받고, 뒤이어 추가 매치 찾기가 시즌 매치를 수백 건 받는다. 두 번째 갱신부터는 새 매치만 받아 훨씬 빠르다. 첫 갱신과 재갱신을 섞지 않으려면 같은 소환사로 바로 다시 돌리지 않는다.
- 앱이 뜨고 1분 뒤, 그 뒤 2시간마다 랭킹 스케줄러가 상위 티어 미등록 소환사 약 1000명을 Riot 에서 조회한다. 부하 중이면 같은 Riot 한도를 쓰므로, 부하 테스트 때는 `LOL_RANKING_SCHEDULER_ENABLED=false`(또는 `--lol.ranking.scheduler.enabled=false`)로 띄워 끈다 (관리자 API 수동 실행은 그대로 된다).
