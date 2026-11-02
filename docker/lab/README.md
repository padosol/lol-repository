# Riot API 실험실

실제 Riot 서버 대신 가짜 서버(`:tools:riot-mock`)로 호출을 보내고, 호출량·429·큐 적체를 Grafana 로 본다.
앱은 `lab` 프로파일로 뜨며, 평소 local DB·Redis·RabbitMQ 와 섞이지 않도록 격리를 강제한다.

## 개발 모드 절차

1. `bash docker/lab/lab.sh up` — 가짜 서버 jar 를 빌드하고 인프라·관측 스택을 띄운다. `lol_lab` DB 와 vhost `lab` 도 만든다.
2. `bash docker/lab/lab.sh app` — 앱을 `local,lab` 프로파일로 띄운다 (포트 8111). 로그에 "Riot 요청을 가짜 서버" WARN 이 한 줄 나온다.
3. `http://localhost:3000` — 실험실 대시보드가 홈이다.
4. `bash docker/lab/lab.sh seed` — 시작 상태(소환사 4000명, 재방문 풀 첫 갱신)를 만들고 `lol_lab_snapshot` 으로 떠 둔다. 한 번만 하면 된다.
5. `bash docker/lab/lab.sh run steady` — 시작 상태로 되돌리고 부하를 건 뒤 판정한다. 몇 번이고 다시 돌릴 수 있다.
6. 끝나면 `bash docker/lab/lab.sh down`. 컨테이너와 볼륨은 남는다.

앱은 seed·run 사이에 다시 띄우지 않는다. run 이 DB 를 스냅숏으로 바꿔도 커넥션 풀이 새 `lol_lab` 에 다시 붙는다.

## 명령

| 명령 | 동작 |
|---|---|
| `up` | `riot-mock` bootJar 빌드, 7개 서비스 기동, `lol_lab` 생성, vhost `lab` 생성, `rabbitmq_prometheus` 활성화 |
| `down` | 7개 서비스를 `stop` 한다. `compose down` 이 아니므로 컨테이너·볼륨(기존 postgres·rabbitmq·redis 데이터)이 그대로다 |
| `app` | `bootRun -Dspring.profiles.active=local,lab` |
| `urls` | Grafana·Prometheus·RabbitMQ·riot-mock 주소 출력 |
| `limit <한도>` | 가짜 서버의 application 한도를 바꾼다. 예: `limit 100:10` |
| `reset` | `lol_lab`·`lol_lab_snapshot` 을 지우고 빈 `lol_lab` 재생성, Redis DB 1 비우기, vhost `lab` 재생성, 가짜 서버 epoch 0. 테이블은 앱 기동 때 Flyway 가 만들므로 **reset 뒤에는 앱을 다시 띄워야 한다** |
| `seed` | 시작 상태를 만들고 `lol_lab_snapshot` 으로 뜬다 (아래 "seed") |
| `load <시나리오>` | k6 부하만 건다. 시작 상태를 되돌리지 않고 판정도 하지 않는다 |
| `run <시나리오>` | 시작 상태 복원 → k6 부하 → 60초 대기 → 판정 (아래 "run"). FAIL 이면 종료 코드 1 |
| `verify <시나리오> <START> <END>` | 판정만 한다 (unix 초). `run` 이 찍은 START·END 로 다시 볼 수 있다 |

`lab.sh` 를 `source` 하면 `require_lab_app`, `mock_get_limits`, `mock_put_limits <json>`, `mock_reset [epoch]` 도 쓸 수 있다.
조작 API 호출이 실패하면 curl 오류나 가짜 서버의 응답 본문(예: 400 의 `error`)을 찍고 멈춘다.

시나리오는 `docker/lab/k6/<시나리오>.js` 다. 1단계에는 `steady` 하나다. `RATE`·`DURATION` 을 셸에서 주면 k6 로 넘긴다 (예: `RATE=10 DURATION=1m bash docker/lab/lab.sh run steady`).

## seed — 시작 상태 만들기

앱이 `lab` 으로 떠 있고 `lol_lab` 이 비어 있을 때 한 번 돌린다 (다시 만들려면 `reset` → 앱 재기동 → `seed`).

1. 가짜 서버 한도를 저장해 두고 application·7개 method 한도를 모두 `100000:10` 으로 올린다. account-v1 의 `1000:60` 이면 Riot ID 조회 4000건이 초당 16건으로 묶인다.
2. 가짜 서버 epoch 를 0 으로 둔다 (재방문 풀도 새 게임 없음).
3. k6 `seed.js` `PHASE=create` — `lab1#LAB`..`lab4000#LAB` 를 `GET /api/riot/kr/summoners/lab{n}/LAB` 로 조회해 저장한다. 200 이 아니거나 puuid 가 다르면 바로 멈춘다.
4. k6 `PHASE=warm` — 재방문 풀(2501..4000)의 첫 갱신을 초당 5건으로 요청한다 (폴링 없음). 모두 202 여야 한다.
5. consumer 가 있는 vhost `lab` 큐의 메시지(대기+처리 중)가 10초 연속 0 이 될 때까지 기다린다 (최대 30분).
6. 저장해 둔 한도 본문을 그대로 PUT 해 되돌린다. 중간에 실패해도 되돌린다.
7. `lol_lab` 새 연결을 막고(`ALLOW_CONNECTIONS false`) 연결을 끊은 뒤 `CREATE DATABASE lol_lab_snapshot TEMPLATE lol_lab` 를 하고 연결을 다시 허용한다. 앱 커넥션 풀이 바로 다시 붙으면 템플릿 복사가 "being accessed by other users" 로 실패하기 때문이다.

warm 을 초당 5건으로 둔 이유: 한도를 올린 채 초당 50건으로 요청하면 consumer 20개가 막힘 없이 돌아 매치 저장(`MatchService.saveAll`)이 10건 넘게 겹친다.
`saveAll` 은 트랜잭션 커넥션을 쥔 채 다른 스레드(`timelineSaveExecutor`)의 저장을 기다리고 그 저장도 커넥션을 하나씩 쓰므로, Hikari 풀(15)이 다 잡혀 앱 전체가 30초 멈췄다 (`Connection is not available, request timed out after 30000ms (total=15, active=15, idle=0, waiting=213)`). 앱 쪽 결함이며 이 실험실이 처음 드러냈다.

큐 대기에서 consumer 가 없는 큐는 뺀다. 이 브랜치에는 PR #130 이 없어 추가 매치 검색 리스너(`renewal.match.find.queue`)가 꺼져 있다.
가짜 서버의 기본 기록은 60게임이므로 첫 갱신은 언제나 20개를 받아 "추가 매치 검색" 메시지를 남기는데, 이 메시지는 처리되지 않는다.
**그래서 첫 갱신은 매치를 20개만 저장한다** (#130 이 들어오면 나머지 40개를 배경에서 더 가져온다).

## run — 시작 상태로 되돌리고 판정하기

1. 앱이 `lab` 인지, `lol_lab_snapshot` 이 있는지 본다 (없으면 "먼저 seed" 로 종료 1).
2. vhost `lab` 큐를 비우고, 처리 중인 갱신이 끝날 때까지(consumer 가 있는 큐가 10초 연속 0) 기다린 뒤 한 번 더 비운다. 지난 실행이 남긴 갱신이 새 실행에 섞이지 않게 한다.
3. `DROP DATABASE lol_lab WITH (FORCE)` → `CREATE DATABASE lol_lab TEMPLATE lol_lab_snapshot`.
4. Redis DB 1 `FLUSHDB` (앱이 배운 한도·한도 카운터·갱신 표시가 지워진다).
5. 앱 `/actuator/health` 가 UP 이 될 때까지 기다린다 (최대 30초). 커넥션 풀이 새 `lol_lab` 에 다시 붙었는지 보는 것이다.
6. 가짜 서버 reset (epoch + 1, 한도 창·슬라이딩 피크 초기화. 한도 값은 그대로).
7. START 를 찍고 k6 시나리오를 돌린 뒤 60초 기다려 END 를 찍는다.
8. `verify.js` 로 판정한다. FAIL 이 있으면 `FAIL` 을 찍고 종료 코드 1, 판정 자체가 실패하면(Prometheus 질의 실패 등) k6 종료 코드를 그대로 낸다.

epoch 가 실행마다 바뀌므로 재방문 소환사의 새 게임 수(0~3)는 실행마다 다르지만 분포는 같아 총 호출량이 비슷하다.

### 판정 항목

한 줄에 `<등급>  <항목>  <값>` 이다. 범위는 [START, END] 이고 질의 시각은 END 다.

- **판정 항목**은 `PASS` 또는 `FAIL` 이다. FAIL 이 하나라도 있으면 run·verify 가 종료 코드 1 로 끝난다.
- **처리 항목**은 `WARN`(나쁨) 또는 `INFO` 다. PASS 로 찍지 않는다. 1단계에서는 WARN 이어도 실행을 실패시키지 않는다 (처리 지연을 판정에 넣는 것은 2단계).

| 구분 | 항목 | 질의 | 등급 |
|---|---|---|---|
| 판정 | 가짜 서버 수집 (창 전체 up) | `min_over_time(up{job="riot-mock"}[R])` | 1 이 아니거나 비면 FAIL |
| 판정 | 가짜 서버 429 (application·method) | `riot_mock_rate_limited_total{type=~"application\|method"}` 증가량 | 0 이 아니면 FAIL. 비면 0 (카운터가 첫 429 때 생긴다) |
| 판정 | 10초 구간 최대 사용률 | `max(riot_mock_sliding_peak_ratio)` | 1 이상이거나 비면 FAIL. run 이 시작할 때 reset 하므로 END 값이 그 실행의 최댓값이다 |
| 판정 | 총 Riot 호출 | `riot_mock_requests_total` 증가량 | 0 이면 FAIL (아무것도 시험하지 않은 실행) |
| 처리 | 앱 갱신 결과 `<result>/<reason>` | `lol_renewal_completed_total` 증가량 (result·reason 별, 0 인 줄은 뺀다) | `failed` 면 WARN, 아니면 INFO. 하나도 없으면 WARN |
| 처리 | k6 갱신 결과 | `k6_lab_renewal_outcome_total` 의 이 실행 마지막 값 | timeout 이나 error 가 있으면 WARN. 없으면 WARN |
| 처리 | 갱신 끝남 p95 | `k6_lab_renewal_done_seconds_p95` 의 이 실행 마지막 값 | 폴링 상한(10초) 이상이면 WARN (값이 상한에서 잘린 것). 없으면 WARN |
| 처리 | 갱신 큐 최대 대기 | consumer 있는 큐의 `rabbitmq_queue_messages_ready` 합의 `max_over_time(...[R:5s])` | 0 보다 크면 WARN. 없으면 WARN |
| 처리 | END 에 남은 갱신 | consumer 있는 큐의 `rabbitmq_queue_messages`(대기+처리 중) 합, END 시각 | 0 보다 크면 WARN (END 에도 갱신이 밀려 있다). 없으면 WARN |

k6 의 `끝남(성공·실패 포함)` 은 폴링에서 PROGRESS 가 끝난 것이다. 앱 쪽에서 실패한 갱신도 여기 들어가므로 성공·실패는 "앱 갱신 결과" 줄로 본다.
큐 항목은 consumer 가 있는 큐만 본다. consumer 없는 `renewal.match.find.queue` 는 늘 쌓이고, 처리 중(unacked) 메시지는 정상이라 최대 대기는 ready 로 센다.
큐가 다 빌 때까지 걸린 시간(소진 시간)은 아직 재지 않는다. 2단계 spike 시나리오에서 다룬다.

증가량은 `increase()` 가 아니라 "END 값 − START 값, START 에 없던 시리즈는 END 값" 이다.
가짜 서버의 429 카운터는 첫 429 때 생기는데, `increase()` 는 창 안에서 처음 생긴 시리즈의 첫 값을 세지 않아 429 가 한 번에 몰리면 0 으로 보일 수 있다.
그래서 창 안에서 가짜 서버나 앱이 재시작하면 값이 맞지 않는다. k6 지표는 실행마다 0 에서 시작하므로 그 실행의 마지막 값을 쓴다.

### 출력 예시 (`run steady`, 기본값 10건/초 · 5분)

```
판정 steady  START=1793620690  END=1793621060  (370초)
PASS  가짜 서버 수집 (창 전체 up)  1
PASS  가짜 서버 429 (application·method)  0
PASS  10초 구간 최대 사용률 (한도 대비)  0.990
PASS  총 Riot 호출  19173
INFO  앱 갱신 결과 success/none  1432
WARN  k6 갱신 결과  끝남(성공·실패 포함)=68 timeout=2766 rejected=160 error=3
WARN  갱신 끝남 p95  10.02s (폴링 상한 10s 에 걸림)
WARN  갱신 큐 최대 대기 (consumer 있는 큐)  1665
WARN  END 에 남은 갱신 (대기+처리 중)  1415
```

판정은 모두 PASS 라 종료 코드 0 이지만, 처리 항목은 갱신이 밀렸다고 말한다 (아래 "읽는 법").

FAIL 이 하나라도 있으면 표 뒤에 `FAIL` 이 찍히고 종료 코드가 1 이다. `limit 30:10` 뒤 `RATE=10 DURATION=1m run steady` 를 돌리면 이렇게 된다.

```
PASS  가짜 서버 수집 (창 전체 up)  1
FAIL  가짜 서버 429 (application·method)  22
FAIL  10초 구간 최대 사용률 (한도 대비)  1.400
PASS  총 Riot 호출  558
INFO  앱 갱신 결과 success/none  34
WARN  앱 갱신 결과 failed/budget_exceeded  94
...
FAIL
```

가짜 서버 지표가 없는 창(수집 전)을 판정하면 `FAIL  가짜 서버 수집 (창 전체 up)  없음`, `FAIL  10초 구간 최대 사용률 (한도 대비)  없음`, `FAIL  총 Riot 호출  0` 으로 실패한다.

### 읽는 법 (1단계 측정)

- 10건/초 중 첫 방문 3건은 매치 20개를 새로 받으므로 asia 호출 수요가 초당 100건을 넘는다. 앱은 `500:10` 을 초당 45건으로 나눠 쓰므로(10초 창에 최대 11칸 × 45 = 495) asia 가 초당 45건 남짓에서 막히고, 슬라이딩 피크가 0.990 에 붙는다.
  그래서 갱신 큐가 쌓이고(대기 최대 1665, END 에도 1415 남음) 대부분 10초 안에 끝나지 않는다(timeout). 429 는 없다.
- `rejected` 는 재방문 풀(1500명)을 한 바퀴 돌아 같은 소환사를 다시 골랐을 때, 큐 적체로 늦게 끝난 지난 갱신의 2분 규칙에 걸린 것이다.
- `error`(3건, 두 번째 실행 6건)는 k6 → `host.docker.internal` 연결이 `dial: i/o timeout` 으로 실패한 것이다. 같은 시각 앱 로그에는 멈춤이 없었다 (Docker Desktop 의 호스트 연결 문제로 본다).
- 갱신 끝남 p95 가 10.02초인 것은 폴링이 10초에서 끝나기 때문이다. 큐가 쌓이면 이 값은 상한에 붙고, 실제 지연은 이보다 길다.
- `limit 30:10` 직후에는 run 이 Redis DB 1 을 비워 앱이 배운 한도를 잃으므로, 첫 응답이 `30:10` 을 알려 줄 때까지 기본값 `500:10` 으로 보내 429 가 난다. 앱이 배운 한도는 대시보드에 없고 Redis DB 1 `riot:rl:limits` 와 앱 로그 `Riot 한도 갱신 [asia|app] 30:10` 으로 본다.

### 걸린 시간 (2026-11-02, Apple Silicon 로컬)

| 단계 | 시간 |
|---|---|
| seed 전체 | 327초 (create 12초, warm 요청 301초, 큐 비우기 10초, 스냅숏 4초) |
| run steady (기본값) | 약 7분 (복원·대기 20~40초 + 부하 5분 + 60초 + 판정) |
| run steady (`RATE=10 DURATION=1m`) | 약 3분 |

## 포트

| 포트 | 대상 | 비고 |
|---|---|---|
| 3000 | Grafana | 로그인 없음 (127.0.0.1 에만 연다) |
| 9090 | Prometheus | 1초 수집, 보존 2일, remote write 수신 |
| 8099 | riot-mock | 조작 API `/admin/*`, 지표 `/actuator/prometheus` |
| 9121 | redis_exporter | |
| 8111 | 앱 | `/actuator/prometheus` 를 Prometheus 가 `host.docker.internal` 로 수집 |
| 15672 | RabbitMQ 관리 화면 | |
| 15692 | RabbitMQ 지표 | compose 네트워크 안에서만 연다 (호스트에 공개하지 않는다). 확인은 Prometheus 질의로 한다 |

Grafana·Prometheus·riot-mock·redis_exporter 는 인증이 없어 `127.0.0.1` 에만 공개한다.

기본 서비스(postgres·rabbitmq·redis)의 설정을 `docker/lab/compose.yml` 에서 바꾸면 compose 가 기존 컨테이너를 재생성해 데이터가 사라질 수 있다. 그래서 15692 를 공개하지 않는다.

## 확인된 동작

- 앱이 뜨면 Prometheus `lol-app`·`riot-mock`·`rabbitmq`·`redis` 4개 job 이 모두 `up` 이다.
- 대시보드는 Grafana 홈이다 (`/api/dashboards/home` 이 실험실 대시보드로 보낸다).
- 요청이 한 번도 없으면 패널 1·2·3 은 모두 0 으로 보인다 (패널 2·3 은 `noValue` 설정).
- `RIOT_API_KEY` 가 환경에 있으면 `LabIsolationGuard` 가 `riot.api-key` 를 이름만 대고 기동을 막는다. 값은 로그에 나오지 않는다.
- `dev,lab`·`prod,lab` 처럼 dev·prod 와 lab 을 같이 켜면 `LabIsolationGuard` 가 빈 생성 전에 `riot.mock.base-url` 을 이름만 대고 막는다 (DB·Redis 연결 전).
- seed 뒤 Redis DB 1 에는 `riot:rl:limits` 가 생기고, DB 0 의 `riot:rl:*` 키 수(0)와 vhost `/` 큐 메시지 수(모두 0)는 seed 전과 같다.
- run 이 앱을 띄운 채 `lol_lab` 을 바꿔도 Hikari 가 끊긴 연결을 버리고("Failed to validate connection") 새로 붙어 health 가 UP 이다. 두 번째 run 도 FAIL 없이 끝났다.
- 같은 스냅숏에서 `run steady` 를 두 번 돌린 총 Riot 호출은 19173 · 19266 (0.5% 차이)이다.
- 실행 중 k6 표본은 Prometheus 에 1초 안에 들어오고, 대시보드는 1초마다 새로 그린다.

## k6 지표 (Prometheus remote write)

부하 스크립트(seed·steady)만 `lab.sh` 가 `-o experimental-prometheus-rw` 로 지표를 보낸다. 판정 `verify.js` 는 보내지 않는다.
주소·trend 통계·전송 주기(1초)는 `compose.yml` 의 `K6_PROMETHEUS_RW_*` 에 있다. 기본 5초면 패널 8 의 `rate(...[5s])` 가 비어 보인다.

| 지표 | 이름 | 태그 |
|---|---|---|
| 요청 수 | `k6_http_reqs_total` | `name`(`seed-create`·`seed-warm`·`renewal-post`·`renewal-poll`), `method`, `status`, `scenario`, `expected_response` |
| 갱신 끝남 시간 (POST 부터 PROGRESS 가 끝난 폴링까지, 10초 안에 끝난 것만) | `k6_lab_renewal_done_seconds_p95` (`_p99`, `_avg`) | `scenario` |
| 갱신 결과 | `k6_lab_renewal_outcome_total` | `outcome`(`done`=끝남, 성공·실패 포함 · `timeout` · `rejected` · `error`), `scenario` |

`url` 태그는 소환사마다 달라 시리즈가 수천 개로 늘어나므로 보내지 않는다 (`systemTags`).
trend 는 실행 시작부터의 누적 값이다 (마지막 값이 k6 요약의 `p(95)` 와 같다). 카운터는 실행마다 0 에서 다시 센다.

## 이미지 버전

| 이미지 | 버전 |
|---|---|
| `prom/prometheus` | v3.15.1 |
| `grafana/grafana` | 13.2.5 |
| `oliver006/redis_exporter` | v1.96.0 |
| `grafana/k6` | 2.3.0 |
| `eclipse-temurin` (riot-mock) | 21-jre |
| `postgres` / `rabbitmq` / `redis` | 기본 `docker/docker-compose.yml` 그대로 |

## `.env` 키 충돌

`bootRun` 은 루트 `.env` 의 모든 줄을 환경변수로 넣고, 이것이 yml 값을 덮는다. lab 은 격리 값이 어긋나면 기동에 실패한다.

| 키 | lab 에서의 영향 | 대처 |
|---|---|---|
| `RIOT_API_KEY` | `riot.api-key` 를 덮어 가드가 기동을 막는다 (실제 키가 가짜 서버로 갈 뻔함) | lab 을 돌릴 때 `.env` 에서 주석 처리하거나, 이 키가 없는 체크아웃에서 돌린다 |
| `DB_HOST`, `DB_PORT` | lab URL 에 그대로 쓰인다 | lab postgres(localhost:5432)를 가리켜야 한다 |
| `DB_NAME` | 무시된다 (URL 에 `lol_lab` 고정) | 없음 |
| `DB_USERNAME`, `DB_PASSWORD` | lab 기본값(postgres/1234)을 덮는다 | lab postgres 계정과 같아야 한다 |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | local 과 같이 쓰인다 | vhost `lab` 에 권한이 있어야 한다 (`up` 이 guest 에 준다) |
| `SPRING_PROFILES_ACTIVE` | `bootRun` 의 `-D` 가 이긴다 | 없음 |
| `PROMETHEUS_SCRAPE_HOST_IP` | 무관 | 없음 |

`.env` 에 `SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_DATABASE`, `SPRING_RABBITMQ_VIRTUAL_HOST` 가 있어도 가드가 기동을 막는다.
가드 메시지에는 어긋난 속성 이름과 기대값만 나오고 실제 값은 나오지 않는다.
