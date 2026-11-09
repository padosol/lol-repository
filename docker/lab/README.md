# Riot API 실험실

실제 Riot 서버 대신 가짜 서버(`:tools:riot-mock`)로 호출을 보내고, 호출량·429·큐 적체를 Grafana 로 본다.
앱은 `lab` 프로파일로 뜨며, 평소 local DB·Redis·RabbitMQ 와 섞이지 않도록 격리를 강제한다.
앱·가짜 서버·인프라·관측 스택이 모두 Docker 컨테이너다 (앱은 compose 서비스 `app`, 컨테이너 `lol-lab-app`).
`docker/lab` 에서 `docker compose up -d --build` 하나로 모두 뜬다. jar 는 이미지 안에서 소스로 빌드하므로 호스트에 JDK 가 없어도 된다.

```bash
cd docker/lab
docker compose up -d --build   # 처음엔 이미지 안 Gradle 빌드로 몇 분 걸린다
docker compose ps              # app 이 (healthy) 가 되면 준비 끝
```

기동 순서는 compose 가 정한다: postgres·rabbitmq healthy → `init-db`(`lol_lab` 생성)·`init-vhost`(vhost `lab` 생성, guest 권한) 완료 후 종료 → `app`.
두 init 은 이미 있으면 그대로 두므로 몇 번을 다시 올려도 된다.

> 호출 경로 (2026-11-09 설계 이후): 호출하는 쪽이 `RiotPermitPort.tryAcquire` 로 허가를 먼저 받고, 거절되면 기다리지 않고 절반으로 줄여(20 → 10 → 5) 다시 묻는다. 응답 뒤 `RiotCallRecorder` 가 기록을 응답 시각으로 옮기고 한도를 배우며 정지를 건다. 아무것도 다시 보내지 않는다. 대시보드의 허가 패널은 `riot_permit_requests_total{routing,priority,result}` 를 본다.

## 개발 모드 절차

1. `bash docker/lab/lab.sh up` (또는 위의 `docker compose up -d --build`) — 이미지를 소스에서 빌드하고 모든 서비스를 띄운 뒤, 앱 health 가 UP 이 될 때까지 기다린다 (포트 8111). 앱 로그에 "Riot 요청을 가짜 서버" WARN 이 한 줄 나온다.
2. `http://localhost:3000` — 실험실 대시보드가 홈이다.
3. `bash docker/lab/lab.sh seed` — 시작 상태(소환사 4000명, 재방문 풀 첫 갱신)를 만들고 `lol_lab_snapshot` 으로 떠 둔다. 한 번만 하면 된다.
4. `bash docker/lab/lab.sh run steady` — 시작 상태로 되돌리고 부하를 건 뒤 판정한다. 몇 번이고 다시 돌릴 수 있다.
5. 끝나면 `bash docker/lab/lab.sh down`. 컨테이너와 볼륨은 남는다.

- 앱은 seed·run 사이에 다시 띄우지 않는다. run 이 DB 를 스냅숏으로 바꿔도 커넥션 풀이 새 `lol_lab` 에 다시 붙는다.
- 코드를 바꾸면 `bash docker/lab/lab.sh app` 으로 앱만 다시 빌드해 띄운다. 앱 로그는 `bash docker/lab/lab.sh logs` 로 본다.
- compose 프로젝트 이름이 `lol-repository` 로 고정이라, `up`·`app`·`reset` 을 돌린 체크아웃(worktree)의 소스로 이미지가 만들어진다.
- postgres 데이터는 이름 있는 볼륨 `lol-postgres-data` 에 있어 `docker compose down` 뒤에도 seed·스냅숏이 남는다. `down -v` 를 하면 지워진다.

## 명령

| 명령 | 동작 |
|---|---|
| `up` | `docker compose up -d --build` (이미지 빌드, 모든 서비스 기동, `init-db`·`init-vhost` 뒤 앱 기동) 다음 앱 health UP 까지 기다린다 (최대 180초) |
| `down` | 모든 서비스를 `stop` 한다. `compose down` 이 아니므로 컨테이너가 그대로다 |
| `app` | 앱 이미지를 소스에서 다시 빌드하고 앱 컨테이너를 새로 만들어 띄운다 (`--no-deps`: 인프라는 건드리지 않는다). 코드를 바꾼 뒤 쓴다 |
| `logs` | 앱 컨테이너 로그를 따라간다 (`compose logs -f --tail 200 app`) |
| `urls` | 앱·Grafana·Prometheus·RabbitMQ·riot-mock 주소 출력 |
| `limit <한도>` | 가짜 서버의 application 한도를 바꾼다. 예: `limit 100:10` |
| `reset` | 앱을 멈추고 `lol_lab`·`lol_lab_snapshot` 을 지운 뒤 빈 `lol_lab` 재생성, Redis DB 1 비우기, vhost `lab` 재생성, 가짜 서버 epoch 0, 앱 다시 기동. 테이블은 앱 기동 때 Flyway 가 만든다. 끝나면 바로 `seed` 를 돌릴 수 있다 |
| `seed` | 시작 상태를 만들고 `lol_lab_snapshot` 으로 뜬다 (아래 "seed") |
| `load <시나리오>` | 앱이 `lab` 인지 확인한 뒤 k6 부하만 건다. 시작 상태를 되돌리지 않고 판정도 하지 않는다 |
| `run <시나리오>` | 시작 상태 복원 → k6 부하 → 60초 대기 → 판정 (아래 "run"). FAIL 이면 종료 코드 1 |
| `verify <시나리오> <START> <END>` | 판정만 한다 (unix 초). `run` 이 찍은 START·END 로 다시 볼 수 있다 |

`lab.sh` 는 `bash` 로 실행만 한다 (`source` 하지 않는다). 가짜 서버 조작 API 를 직접 부르려면 curl 을 쓴다 (예: `curl -s localhost:8099/admin/limits`).
lab.sh 의 조작 API 호출이 실패하면 curl 오류나 가짜 서버의 응답 본문(예: 400 의 `error`)을 찍고 멈춘다.

셸 환경변수로 바꿀 수 있는 값:

| 변수 | 기본값 | 쓰는 곳 |
|---|---|---|
| `APP_URL` | `http://localhost:8111` | 호스트에서 앱 확인(`/actuator/info`·`health`) |
| `K6_APP_URL` | `http://app:8111` | k6 컨테이너에 `-e APP_URL` 로 넘기는 앱 주소 (같은 compose 네트워크, k6 스크립트에는 기본값이 없다). Prometheus 수집 대상은 `prometheus.yml` 의 `app:8111` 고정이다 |
| `LAB_APP_JAVA_OPTS` | `-Xmx3g` | 앱 컨테이너 JVM 옵션. Docker Desktop VM 메모리(약 8GB)를 다른 컨테이너와 나눠 쓴다 |
| `MOCK_URL` | `http://localhost:8099` | 가짜 서버 조작 API |
| `LAB_PROMETHEUS_PORT`·`LAB_GRAFANA_PORT` | `9090`·`3000` | 호스트에 여는 Prometheus·Grafana 포트. 그 포트가 이미 쓰이고 있으면 다른 값을 준다 (compose, `PROM_URL` 기본값, `urls` 출력이 따른다) |
| `PROM_URL` | `http://localhost:9090` | seed 의 결과 확인 질의 |
| `LAB_QUEUES` | `mmrtr.summoner mmrtr.renewal.account mmrtr.renewal.league mmrtr.renewal.match renewal.match.find.queue` | 갱신 처리를 기다리고 판정할 vhost `lab` 큐 이름(공백 구분). 아래 "큐 고르기" |

시나리오는 `docker/lab/k6/<시나리오>.js` 다. `steady`(일정한 부하)와 `burst`(주기적으로 몰려오는 부하)가 있다. `RATE`·`DURATION` 을 셸에서 주면 k6 로 넘긴다 (예: `RATE=10 DURATION=1m bash docker/lab/lab.sh run steady`).

`burst` 는 `BURST_INTERVAL`(초, 기본 30)마다 `BURST_SIZE`(기본 6)건의 첫 방문 갱신을 같은 시각에 보내고 `BURST_ROUNDS`(기본 10)번 반복한다 (예: `BURST_SIZE=6 BURST_INTERVAL=30 bash docker/lab/lab.sh run burst`). 평균 수요(기본 초당 0.2건)가 처리 한계보다 낮아야 평균이 아니라 몰림 자체의 효과만 보인다.
`BURST_INTERVAL=15 BURST_ROUNDS=20`(backlog 변형, 5분)은 버스트가 남긴 백필 일이 초당 40회로 다 빠지기 전에 다음 버스트가 와, 유저 호출이 밀린 백필과 자리를 다투게 한다 (아래 "한도 관리자 비교").

## seed — 시작 상태 만들기

앱이 `lab` 으로 떠 있고 `lol_lab` 이 비어 있을 때 한 번 돌린다 (다시 만들려면 `reset` → `seed`).

0. `lol_lab` 의 `riot.summoner` 가 비어 있지 않으면 아무것도 바꾸지 않고 종료 1 이다 (`lol_lab 이 비어 있지 않습니다 (riot.summoner 4000명). 먼저 bash docker/lab/lab.sh reset 을 하세요 (앱도 다시 띄운다)`). 테이블이 없으면(앱이 Flyway 를 돌리기 전이면) `lab.sh app` 으로 앱을 다시 띄우라고 하고 종료 1 이다. 이미 돌린 `lol_lab` 에 다시 seed 하면 매치가 있는 첫 방문 소환사가 스냅숏에 들어가기 때문이다.
1. 가짜 서버 한도를 저장해 두고 application·7개 method 한도를 모두 `100000:10` 으로 올린다. account-v1 의 `1000:60` 이면 Riot ID 조회 4000건이 초당 16건으로 묶인다.
2. 가짜 서버 epoch 를 0 으로 둔다 (재방문 풀도 새 게임 없음).
3. k6 `seed.js` `PHASE=create` — `lab1#LAB`..`lab4000#LAB` 를 `GET /api/riot/kr/summoners/lab{n}/LAB` 로 조회해 저장한다. 200 이 아니거나 puuid 가 다르면 바로 멈춘다.
4. k6 `PHASE=warm` — 재방문 풀(2501..4000)의 첫 갱신을 초당 5건으로 요청한다 (폴링 없음). 모두 202 여야 한다.
5. `LAB_QUEUES` 큐의 메시지(대기+처리 중)가 10초 연속 0 이 될 때까지 기다린다 (최대 30분). 진행 줄에 consumer 수를 같이 찍는다. 큐가 없으면 바로 실패한다.
6. 결과를 확인한다. 소환사가 4000명이고, warm 시작부터 지금까지 앱의 `lol_renewal_completed_total` 증가량이 `success` 정확히 1500건, 그 밖(`failed`·`skipped`) 0건이어야 한다 (Prometheus 질의, verify 와 같은 증가량 식). 아니면 스냅숏을 뜨지 않고 종료 1 이다. 한도는 되돌리고, `lol_lab` 연결 허용은 건드리지 않은 상태다.
7. 저장해 둔 한도 본문을 그대로 PUT 해 되돌린다. 중간에 실패해도 되돌린다.
8. `lol_lab` 새 연결을 막고(`ALLOW_CONNECTIONS false`) 연결을 끊은 뒤 `CREATE DATABASE lol_lab_snapshot TEMPLATE lol_lab` 를 하고 연결을 다시 허용한다. 앱 커넥션 풀이 바로 다시 붙으면 템플릿 복사가 "being accessed by other users" 로 실패하기 때문이다.

warm 을 초당 5건으로 둔 이유: 한도를 올린 채 초당 50건으로 요청하면 consumer 20개가 막힘 없이 돌아 매치 저장(`MatchService.saveAll`)이 10건 넘게 겹친다.
`saveAll` 은 트랜잭션 커넥션을 쥔 채 다른 스레드(`timelineSaveExecutor`)의 저장을 기다리고 그 저장도 커넥션을 하나씩 쓰므로, Hikari 풀(15)이 다 잡혀 앱 전체가 30초 멈췄다 (`Connection is not available, request timed out after 30000ms (total=15, active=15, idle=0, waiting=213)`). 앱 쪽 결함이며 이 실험실이 처음 드러냈다.

create 가 `lab27#LAB 조회 실패: status=500` 처럼 몇 초 만에 멈출 때가 있다 (2026-11-08, 앱 컨테이너에서 두 번 중 한 번).
- **원인:** 빈 DB 에서 같은 리그(`lab-league-0..6`)의 소환사 둘이 동시에 저장됐다. 앱 로그에는 `duplicate key value violates unique constraint "pk_league"` 가 남는다.
- **앱 결함인 이유:** `LeagueRepositoryImpl.saveIfAbsent` 는 Redis 락 안에서 "조회 → 없으면 저장" 을 한다. 그런데 바깥 `@Transactional`(`SaveSummonerDataUseCase.saveLeagues`)이 커밋하기 전에 락이 풀린다. 그래서 다음 요청도 행을 보지 못하고 같은 리그를 INSERT 한다.
- **대처:** `reset` 뒤 `seed` 를 다시 돌린다. 리그가 한 번 생기면 더는 나지 않는다.

### 큐 고르기 (`LAB_QUEUES`)

갱신 처리 대기(seed·run)와 판정의 큐 항목은 consumer 가 있게 설계된 큐를 **이름으로** 고른다. 기본값은 위 표의 다섯 큐다.
consumer 수(`> 0`)로 고르면 리스너가 죽어 consumer 가 0 이 됐을 때 그 큐가 빠져 적체가 숨는다. 이름으로 고르면 consumer 가 0 이어도 메시지가 그대로 세어져, 대기는 시간 초과로 실패하고 판정에는 적체가 WARN 으로 남는다.

갱신 단계 큐(`mmrtr.renewal.account`·`league`·`match`)와 추가 매치 검색 큐(`renewal.match.find.queue`)에 모두 consumer 가 있다. 매치 적재 우선순위 큐 `mmrtr.matchId.priority` 는 갱신 완료 판정에 쓰지 않아 목록에 없다 (Grafana "갱신 단계 큐 깊이" 패널에는 있다).
verify.js 는 lab.sh 가 넘긴 값을 쓰므로 따로 고칠 곳이 없다.

## run — 시작 상태로 되돌리고 판정하기

1. 앱이 `lab` 인지, `lol_lab_snapshot` 이 있는지 본다 (없으면 "먼저 seed" 로 종료 1).
2. vhost `lab` 큐를 비우고, 처리 중인 갱신이 끝날 때까지(`LAB_QUEUES` 가 10초 연속 0) 기다린 뒤 한 번 더 비운다. 지난 실행이 남긴 갱신이 새 실행에 섞이지 않게 한다.
3. `DROP DATABASE lol_lab WITH (FORCE)` → `CREATE DATABASE lol_lab TEMPLATE lol_lab_snapshot`.
4. Redis DB 1 `FLUSHDB` (앱이 배운 한도·한도 카운터·갱신 표시가 지워진다).
5. 앱 `/actuator/health` 가 UP 이 될 때까지 기다린다 (최대 30초). 커넥션 풀이 새 `lol_lab` 에 다시 붙었는지 보는 것이다. 앱 컨테이너가 멈춰 있으면 마지막 로그 40줄을 찍고 바로 실패한다.
6. 가짜 서버 reset (epoch + 1, 한도 창·슬라이딩 피크 초기화. 한도 값은 그대로). 5초 기다린 뒤 지금 application 한도를 찍는다.
   기다리는 것은 reset 전 게이지 값(지난 실행의 슬라이딩 피크)을 담은 표본이 창 안에 들지 않게 하려는 것이다. 기다리지 않으면 START 뒤 2초 동안 지난 피크가 보인다 (Task 7 Step 5 창을 지금 판정으로 다시 보면 피크가 그 실행의 1.400 이 아니라 reset 전 값 2.433 이다).
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
| 판정 | 가짜 서버 application 한도 (10초 창, 500 이하) | `riot_mock_window_limit{bucket=~".+[\|]app",window="10"}` 의 창 전체 `max_over_time(max(...)[R:1s])`, 같은 식의 min | 최댓값이 500 보다 크거나(기본값보다 느슨한 한도) 시리즈가 없으면 FAIL. 값은 `min=… max=…` 로 늘 찍는다. 더 엄격한 한도(예: `30:10`)는 통과한다 |
| 판정 | 가짜 서버 429 (application·method) | `riot_mock_rate_limited_total{type=~"application\|method"}` 증가량 | 0 이 아니면 FAIL. 비면(창 안에 요청이 없으면) 0 |
| 판정 | 10초 구간 최대 사용률 | `max_over_time(max(riot_mock_sliding_peak_ratio)[R:1s])` | 1 이상이거나 비면 FAIL. 창 전체의 최댓값이라 창 안에서 application 한도를 바꿔(추적기가 비워져도) 그 전 피크가 남는다 |
| 판정 | 총 Riot 호출 | `riot_mock_requests_total` 증가량 | 0 이면 FAIL (아무것도 시험하지 않은 실행) |
| 처리 | 앱 수집 (창 전체 up) | `min_over_time(up{job="lol-app"}[R])` | 1 보다 작거나 비면 WARN (창 안에서 앱이 멈췄거나 수집이 끊겼다) |
| 처리 | 앱 갱신 결과 `<result>/<reason>` | `lol_renewal_completed_total` 증가량 (result·reason 별, 0 인 줄은 뺀다) | `failed` 면 WARN, 아니면 INFO. 하나도 없으면 WARN |
| 처리 | k6 갱신 결과 | `k6_lab_renewal_outcome_total` 의 이 실행 마지막 값 | timeout 이나 error 가 있으면 WARN. 없으면 WARN |
| 처리 | 갱신 끝남 p95 | `k6_lab_renewal_done_seconds_p95` 의 이 실행 마지막 값 | 폴링 상한(10초) 이상이면 WARN (값이 상한에서 잘린 것). 없으면 WARN |
| 처리 | 갱신 기한 초과 비율 (failed/deadline ÷ 끝남) | `lol_renewal_completed_total{result="failed",reason="deadline"}` 증가량 ÷ 전체 증가량 | 0 보다 크면 WARN |
| 처리 | 앱 갱신 소요 p95 (success) | `histogram_quantile(0.95, lol_renewal_duration_seconds_bucket{result="success"})`. lab 프로파일이 히스토그램을 켠다. 없으면 평균(sum/count)을 찍는다 | INFO |
| 처리 | 매치 처리 user_retry | `lol_match_process_total{rank="user_retry"}` 증가량 | INFO |
| 처리 | 기한 뒤 늦게 끝난 갱신 | `lol_renewal_late_completed_total` 증가량 | INFO |
| 처리 | 갱신 큐 최대 대기 (`LAB_QUEUES`) | 그 큐들의 `rabbitmq_queue_messages_ready` 합의 `max_over_time(...[R:1s])` | 0 보다 크면 WARN. 없으면 WARN |
| 처리 | END 에 남은 갱신 (`LAB_QUEUES`, 대기+처리 중) | 그 큐들의 `rabbitmq_queue_messages` 합, END 시각 | 0 보다 크면 WARN (END 에도 갱신이 밀려 있다). 없으면 WARN |

k6 의 `끝남(성공·실패 포함)` 은 폴링에서 PROGRESS 가 끝난 것이다. 앱 쪽에서 실패한 갱신도 여기 들어가므로 성공·실패는 "앱 갱신 결과" 줄로 본다.
큐 항목은 `LAB_QUEUES`(기본: 위 다섯 큐)만 본다 (위 "큐 고르기"). 처리 중(unacked) 메시지는 정상이라 최대 대기는 ready 로 센다.
큐가 다 빌 때까지 걸린 시간(소진 시간)은 아직 재지 않는다. 2단계 spike 시나리오에서 다룬다.

application 한도 항목은 PASS 가 "기본값 이하의 한도에서 시험했다" 는 뜻이 되게 한다. `limit 100000:10` 뒤나, seed 가 올린 한도가 되돌아가지 않은 상태에서 돌리면 429 와 피크가 당연히 PASS 이므로 이 항목이 FAIL 로 막는다.
`riot_mock_window_limit` 은 그 routing 에 요청이 온 뒤에만 생기므로(reset 하면 사라진다) 창 안에 요청이 없으면 `없음` 으로 FAIL 이다.

증가량은 `increase()` 가 아니라 "END 값 − START 값, START 에 없던 시리즈는 END 값" 이다.
`increase()` 는 창 안에서 처음 생긴 시리즈의 첫 값을 세지 않는다. 가짜 서버는 routing 의 첫 요청 때 429·unknown 카운터를 0 으로 먼저 만들지만, 가짜 서버를 다시 띄운 직후처럼 창 안에서 시리즈가 생길 수 있어 이 식을 쓴다.
그래서 창 안에서 가짜 서버나 앱이 재시작하면 값이 맞지 않는다. k6 지표는 실행마다 0 에서 시작하므로 그 실행의 마지막 값을 쓴다.

### 출력 예시 (`run steady`, 기본값 10건/초 · 5분)

```
시작 상태 복원: lol_lab ← lol_lab_snapshot, Redis DB 1 비움, vhost lab 비움, 가짜 서버 {"epoch":1}, application 한도 500:10
...
판정 steady  START=1793626779  END=1793627149  (370초)
PASS  가짜 서버 수집 (창 전체 up)  1
PASS  가짜 서버 application 한도 (10초 창, 500 이하)  min=500 max=500
PASS  가짜 서버 429 (application·method)  0
PASS  10초 구간 최대 사용률 (한도 대비)  0.990
PASS  총 Riot 호출  19279
INFO  앱 수집 (창 전체 up)  1
INFO  앱 갱신 결과 success/none  1463
WARN  k6 갱신 결과  끝남(성공·실패 포함)=70 timeout=2769 rejected=161 error=0
WARN  갱신 끝남 p95  10.02s (폴링 상한 10s 에 걸림)
WARN  갱신 큐 최대 대기 (mmrtr.summoner)  1656
WARN  END 에 남은 갱신 (mmrtr.summoner, 대기+처리 중)  1394
```

판정은 모두 PASS 라 종료 코드 0 이지만, 처리 항목은 갱신이 밀렸다고 말한다 (아래 "읽는 법").

FAIL 이 하나라도 있으면 표 뒤에 `FAIL` 이 찍히고 종료 코드가 1 이다. `limit 100000:10` 뒤 `RATE=2 DURATION=30s load steady` 를 건 창을 `verify` 하면, 429·사용률은 당연히 통과하고 한도 항목이 막는다.

```
판정 steady  START=1793627189  END=1793627242  (53초)
PASS  가짜 서버 수집 (창 전체 up)  1
FAIL  가짜 서버 application 한도 (10초 창, 500 이하)  min=100000 max=100000
PASS  가짜 서버 429 (application·method)  0
PASS  10초 구간 최대 사용률 (한도 대비)  0.000
PASS  총 Riot 호출  61
INFO  앱 수집 (창 전체 up)  1
INFO  앱 갱신 결과 success/none  61
INFO  k6 갱신 결과  끝남(성공·실패 포함)=61 timeout=0 rejected=0 error=0
INFO  갱신 끝남 p95  1.01s
INFO  갱신 큐 최대 대기 (mmrtr.summoner)  0
INFO  END 에 남은 갱신 (mmrtr.summoner, 대기+처리 중)  0
thresholds on metrics 'checks' have been crossed

FAIL
```

`limit 30:10` 뒤 `RATE=10 DURATION=1m run steady` 는 한도 항목은 통과하고(`min=30 max=30`, 더 엄격한 한도) 429(22건)와 사용률(1.400)이 FAIL 이다.

가짜 서버 지표가 없는 창(수집 전)을 판정하면 `FAIL  가짜 서버 수집 (창 전체 up)  없음`, `FAIL  10초 구간 최대 사용률 (한도 대비)  없음`, `FAIL  총 Riot 호출  0` 으로 실패한다.

### 읽는 법 (1단계 측정)

위 출력 예시와 아래 설명은 1단계 때(1초 칸 한도 관리자, 앱이 호스트에서 돌던 때)의 측정이다. 지금 한도 관리자(ZSET 슬라이딩 창)의 결과는 아래 "한도 관리자 비교" 에 있다.

- 10건/초 중 첫 방문 3건은 매치 20개를 새로 받으므로 asia 호출 수요가 초당 100건을 넘는다. 그때 앱은 `500:10` 을 초당 45건으로 나눠 썼으므로(10초 창에 최대 11칸 × 45 = 495) asia 가 초당 45건 남짓에서 막히고, 슬라이딩 피크가 0.990 에 붙었다.
  그래서 갱신 큐가 쌓이고(대기 최대 1656, END 에도 1394 남음) 대부분 10초 안에 끝나지 않는다(timeout). 429 는 없다.
- `rejected` 는 재방문 풀(1500명)을 한 바퀴 돌아 같은 소환사를 다시 골랐을 때, 큐 적체로 늦게 끝난 지난 갱신의 2분 규칙에 걸린 것이다.
- `error`(3건, 두 번째 실행 6건, 세 번째 0건)는 k6 → `host.docker.internal` 연결이 `dial: i/o timeout` 으로 실패한 것이다. 같은 시각 앱 로그에는 멈춤이 없었다 (Docker Desktop 의 호스트 연결 문제로 본다. 앱이 호스트에서 돌던 때의 측정이고, 지금은 k6 가 compose 네트워크 안에서 `app:8111` 로 간다).
- 갱신 끝남 p95 가 10.02초인 것은 폴링이 10초에서 끝나기 때문이다. 큐가 쌓이면 이 값은 상한에 붙고, 실제 지연은 이보다 길다.
- `limit 30:10` 직후에는 run 이 Redis DB 1 을 비워 앱이 배운 한도를 잃으므로, 첫 응답이 `30:10` 을 알려 줄 때까지 기본값 `500:10` 으로 보내 429 가 난다. 앱이 배운 한도는 대시보드에 없고 Redis DB 1 `riot:rl:limits` 와 앱 로그 `Riot 한도 갱신 [asia|app] 30:10` 으로 본다.

#### 한도 관리자 비교 (기준선 = 1초 칸 방식 (같은 갱신 코드), 새 방식 = ZSET 슬라이딩 창)

> 참고: 이 절의 `riot_ratelimit_wait_*`·`riot_ratelimit_acquire_attempts_total`·`riot_ratelimit_rejected_total` 지표는 2026-11-09 설계로 사라졌다. 당시 측정을 그대로 남긴다.

- 기준선은 1초 칸 한도 관리자, "새 방식 (흩기 전)" 은 ZSET 슬라이딩 창의 첫 측정, "새 방식" 은 지금 코드다. 지금 코드는 기다리던 요청을 흩어 깨운다 (최소 25ms + 대기의 25% 까지 무작위 몫, 설계 4.4).
- 칸마다 **한 번씩만** 돌린 값이다. 같은 조건을 되풀이해 흩어짐을 잰 것이 아니다.
- 기준선 steady 는 앱이 호스트(`bootRun`)에서 돌던 때의 측정이고(START=1793721477 END=1793721845), 나머지는 모두 앱 컨테이너다 (아래 "앱 컨테이너 설정"). burst backlog 기준선은 이 브랜치 바로 앞 커밋 `3f7d0ca` 로 빌드한 앱이다.

| 시나리오 | 항목 | 기준선 | 새 방식 (흩기 전) | 새 방식 |
|---|---|---|---|---|
| burst | 갱신 기한 초과 비율 | 0.00% (0/60) | 0.00% (0/60) | 0.00% (0/60) |
| burst | 앱 갱신 소요 p95 (success) | 2.69s | 0.89s | 1.22s |
| burst | 갱신 끝남 p95 | 4.52s | 1.02s | 2.52s |
| burst | 10초 구간 최대 사용률 | 0.846 | 0.450 | 0.450 |
| burst | 총 Riot 호출 | 4260 | 4260 | 4260 |
| burst | 갱신 큐 최대 대기 | 2 | 4 | 3 |
| steady | 갱신 기한 초과 비율 | 64.05% (1922/3001) | 63.81% (1915/3001) | 63.71% (1912/3001) |
| steady | 앱 갱신 소요 p95 (success) | 0.11s | 0.11s | 0.11s |
| steady | 갱신 끝남 p95 | 6.51s | 6.51s | 6.51s |
| steady | 10초 구간 최대 사용률 | 0.990 | 0.900 | 0.900 |
| steady | 총 Riot 호출 | 21465 | 21270 | 21200 |
| steady | 갱신 큐 최대 대기 / END 에 남은 갱신 | 1417 / 1169 | 1299 / 1165 | 1313 / 1171 |
| steady | k6 timeout | 0 (error 1) | 0 | 0 |
| steady | 매치 처리 user_retry | 0 | 18 | 48 |
| burst backlog | 갱신 기한 초과 비율 | 0.00% (0/120) | — | 0.00% (0/120) |
| burst backlog | 앱 갱신 소요 p95 (success) | 3.53s | — | 1.07s |
| burst backlog | 갱신 끝남 p95 | 4.53s | — | 2.51s |
| burst backlog | 10초 구간 최대 사용률 | 0.822 | — | 0.888 |
| burst backlog | 총 Riot 호출 | 8522 | — | 8520 |
| burst backlog | 갱신 큐 최대 대기 | 3 | — | 6 |

- steady 는 세 칸이 거의 같다. 수요(asia 초당 약 126회)가 처리 한계(초당 약 45회)를 넘는 과부하라 줄이 서고, 과부하 대책은 설계의 비목표다 (Plan 2 의 접수 거절이 맡는다). 바뀐 것은 사용률이 0.990 에서 0.900 으로 내려온 것이다 (상한 450 = 500 × 0.9 를 아무 10초 구간에서 지킨다). 429 는 세 칸 모두 0건이다.
- burst 의 사용률 0.450 = 225/500 = 유저 활동 중 백필 상한(450 × 0.5)이다. 유저 활동 중에는 백필이 창 전체 개수 225 까지만 나가고, 버스트 하나의 asia USER 호출(이 창의 asia USER 허가 1440건 ÷ 10회 = 144)은 남은 225 안에 들어간다. 그래서 이 시나리오는 유저가 백필과 자리를 다투는 경우를 시험하지 않는다 (아래 backlog 변형).
- burst backlog 는 `BURST_INTERVAL=15 BURST_ROUNDS=20` 이다 (6건씩 20회, 5분). 다음 버스트 직전(15초 간격 표본)에도 백필 큐 `mmrtr.matchId.priority` 가 비지 않았다 (기준선 15~58건, 새 방식 3~53건. 기본 burst 는 다음 버스트 전에 0 이다). 그래서 유저 호출이 밀린 백필과 자리를 다툰 실행이다.
  - 새 방식은 앱 갱신 소요 p95 가 3.53s → 1.07s, 갱신 끝남 p95 가 4.53s → 2.51s 로 짧다. 기한 초과는 둘 다 0건, 429 는 둘 다 0건이고 사용률은 0.822 → 0.888 로 둘 다 1 미만이다.
  - 같은 창의 대기(`riot_ratelimit_wait_seconds`)는 USER 가 평균 0.839s → 0.001s(p95 2.13s → 0.001s)로 줄고, 백필(BACKGROUND)이 평균 0.732s → 1.446s(p95 1.82s → 9.27s)로 늘었다. 설계 목표(유저 먼저, 백필은 남는 만큼)와 같은 방향이다.
- 지금 코드의 burst 는 흩기 전보다 앱 갱신 소요 p95 가 0.89s → 1.22s, 갱신 끝남 p95 가 1.02s → 2.52s 로 나빠졌다 (기준선 2.69s·4.52s 보다는 낫다). 원인은 확인하지 못했다 (원인 미확인). 갱신 끝남은 k6 폴링 시각(POST 뒤 1·2.5·4.5·6.5·8.5·10초)으로만 찍히므로, 95번째 갱신이 첫 폴링(1초) 뒤에 끝나면 2.5초대로 찍힌다.
- 지금 코드의 steady 는 USER 거절이 흩기 전보다 늘었다 (아래 Redis 표, 매치 처리 user_retry 18 → 48). 같은 창의 USER 대기는 평균 1.316s → 1.439s, p95 7.17s → 7.71s 로 길어졌다 (`riot_ratelimit_wait_seconds` 히스토그램). 거절이 늘어난 원인은 확인하지 못했다 (원인 미확인).
- burst 의 백필(BACKGROUND)은 5초 평균으로 최대 40.03회/초(흩기 전)·40.02회/초(지금)였다 (`max_over_time((sum(increase(riot_ratelimit_wait_seconds_count{job="lol-app",priority="BACKGROUND"}[5s])) / 5)[334s:1s])`, 시각 = END). `increase()` 는 창 끝까지 외삽하므로 40 을 조금 넘게 나올 수 있다. 설계 6.2.5 가 보라고 한 것은 1초 증가량이다. 1초 간격 수집 사이 차이(`max_over_time((sum(X) - sum(X offset 1s))[334s:1s])`, X = 위 카운터)로는 두 실행 모두 최대 40 이었다. 이 값은 앱 시각과 수집 간격으로 센 것이고, Lua 는 Redis 시각 기준 아무 1초에 40회 이하로만 허가한다 (백필 전용 1초 규칙).

##### Redis 부하 (기다리던 요청이 한꺼번에 깨는 문제)

| 실행 | EVALSHA 호출 | Riot 호출(허가) | EVALSHA ÷ 허가 | acquire 호출 ÷ 허가 | 최대 EVALSHA/s | Redis CPU 최대 / 평균 | USER 거절 |
|---|---|---|---|---|---|---|---|
| steady 기준선 | 66,274 | 21,465 | 3.09 | — | 259 | 4.1% / 3.1% | 0 |
| steady 새 방식 (흩기 전) | 1,002,413 | 21,270 | 47.13 | — | 7,926 | 12.1% / 8.8% | 21 |
| steady 새 방식 | 482,191 | 21,200 | 22.74 | 19.72 | 3,511 | 9.0% / 6.9% | 59 |
| burst 기준선 | 14,264 | 4,260 | 3.35 | — | 198 | 1.7% / 0.8% | 0 |
| burst 새 방식 (흩기 전) | 42,518 | 4,260 | 9.98 | — | 666 | 2.1% / 1.1% | 0 |
| burst 새 방식 | 24,283 | 4,260 | 5.70 | 4.40 | 568 | 2.2% / 1.0% | 0 |
| burst backlog 기준선 | 30,939 | 8,522 | 3.63 | — | 199 | 1.7% / 1.1% | 0 |
| burst backlog 새 방식 | 77,754 | 8,520 | 9.13 | 7.83 | 767 | 3.7% / 1.8% | 0 |

- 질의 (범위 R = END − START, 시각 = END. 증가량은 판정과 같이 "END 값 − START 값, START 에 없던 시리즈는 0"):
  - EVALSHA: `sum(redis_commands_total{job="redis",cmd="evalsha"})` 증가량. Redis 의 모든 Lua 스크립트(갱신 상태 등)를 함께 센다.
  - Riot 호출: `sum(riot_mock_requests_total{job="riot-mock"})` 증가량.
  - acquire 호출: `sum(riot_ratelimit_acquire_attempts_total{job="lol-app"})` 증가량. 흩기와 함께 생긴 카운터라 그 전 실행에는 없다.
  - 최대 EVALSHA/s: `max_over_time(sum(rate(redis_commands_total{job="redis",cmd="evalsha"}[5s]))[Rs:1s])`.
  - Redis CPU: `sum(rate(redis_cpu_sys_seconds_total{job="redis"}[10s]) + rate(redis_cpu_user_seconds_total{job="redis"}[10s]))` 의 `max_over_time`·`avg_over_time` (`[Rs:1s]`).
  - USER 거절: `sum(riot_ratelimit_rejected_total{job="lol-app",priority="USER"})` 증가량. `increase()` 로 세면 흩기 전 steady 는 20.05, 지금 steady 는 55.12 다 (창 안에서 처음 생긴 시리즈의 첫 값을 세지 않는다).
- 흩은 뒤 steady 의 EVALSHA 는 약 절반(47.13 → 22.74 / 허가)이고 최대 초당 호출과 Redis CPU 도 줄었지만, 기준선(3.09)보다는 여전히 많다.

#### 초당 10건 재확인과 백필 (2026-11-06, steady 기본값, 조건마다 한 번)

| 실행 | 429 | 10초 최대 사용률 | 갱신 성공 | 늦은 완료 | USER 거절 | 부하 뒤 남은 유저 일 |
|---|---|---|---|---|---|---|
| a483f92 (USER 흔들림 대기의 25%) | 0 | 0.900 | 1087 (36%) | 1204 | 62 | 약 4분 |
| 이미 FAILED 인 갱신의 매치를 백필로 (되돌림) | 0 | 0.900 | 806 (27%) | 795 | 11 | 약 30초 |
| 위 + 매치 단계 소비자 20 → 5 (버림) | 0 | 0.900 | 897 (30%) | 762 | 38 | 약 30초 |
| USER 흔들림 최대 50ms (지금 코드) + 매치 단계 소비자 5 (버림) | 0 | **1.286 (FAIL)** | 1094 (36%) | 1203 | 23 | 약 4분 |

- **수요가 예산의 약 3.4배다.** 첫 방문 30% 면 asia 수요가 초당 약 150회(첫 방문 3건 × 42 + 재방문)인데 예산은 약 45회다. 한도 관리자는 429 없이 450/10초를 지키지만 갱신의 약 64% 는 5초를 넘긴다. 위 세 가지 손질로는 성공 비율이 나아지지 않았다. Plan 2 의 접수 거절 몫이다.
- **이미 FAILED 인 갱신의 매치를 백필로 넘기면 손해였다.** 부하 중 백필은 0건이라 넘긴 매치가 들어오지 않고, 같은 소환사를 다시 누를 때 늦은 완료를 받지 못해 처음부터 다시 받는다 (갱신 완료 설계 G3·8장).
- **매치 단계 소비자 수는 성공 비율에 영향이 없었다** (20개 1087 · 5개 1094). FIFO 큐에 밀린 일이 결과를 정한다.
- **USER 흔들림을 최대 50ms 로 묶은 세 실행** 모두 USER 거절이 62 보다 적었다 (11 · 38 · 23). 소비자 20개 그대로 흔들림만 묶은 실행은 따로 재지 않았다.
- **백필은 부하 중 사실상 0건이다.** 유저 활동 중이라 창의 절반까지만 쓸 수 있는데 유저가 창을 다 쓴다. 추가 매치 찾기 큐가 초당 약 1.3건씩 쌓여(5분에 약 450건) 부하 뒤 유저 일 4분이 끝난 다음에야 초당 40회로 빠진다. 다 비우는 데 25분 넘게 걸린다 (남은 양으로 추정).
- **백필의 DB 쓰기가 실험실 Postgres 를 막았다.** 백필이 초당 40회(약 20경기, 타임라인 포함)로 8분쯤 쓰자 WAL 체크포인트가 1~2분마다 돌았다. 그 뒤 앱의 커넥션 풀(15)이 모두 저장에 묶이고 대기가 47건까지 쌓여 백필이 초당 2회로 떨어졌다. 유저 저장도 같은 풀을 쓴다. 스냅숏은 6.5GB(participant_frame 5.4GB)이고 run 마다 통째로 복사한다.
- **1.286 은 원인 미확인이다.** 가짜 서버의 100ms 집계로 asia 10초 구간에 643건이 들어갔다. 앱의 허가는 1초 카운터로 아무 10초에도 450 이하였다. 같은 시각 모든 수집 대상(앱·가짜 서버·Redis·RabbitMQ)의 수집이 2초씩 빠졌다. 고정 창으로 세는 가짜 서버는 429 를 내지 않았다. 다른 네 번의 steady 는 모두 0.900 이었다.

#### 초당 몇 건까지 p95 3초 안인가 (2026-11-06, steady 첫 방문 30%, 각 3분 · 한 번씩)

| 초당 갱신 | 3초 안에 끝난 비율 | 전체 갱신 소요 p95 | 5초 기한 초과 | 백필 asia / 갱신 1건 |
|---|---|---|---|---|
| 1 | 100% | 0.67s | 0% | 12.6 |
| 2 | 100% | 0.62s | 0% | 8.0 |
| 3 | 100% | 0.62s | 0% | — |
| 4 | 99.9% | 0.63s | 0% | 3.1 |
| 5 | 22.6% | 5.63s | 69% | 1.8 |
| 6 | 19.2% | 5.64s | 76% | 0.1 |
| 7 | 23.2% | 5.63s | 73% | 0 |

- 전체 갱신 소요는 `lol_renewal_duration_seconds` 히스토그램의 성공·실패 전부다. "3초 안" 은 3초 바로 아래 칸(2.86초)까지로 셌다. 증가량은 부하 시작 15초 뒤 값을 기준으로 했다 (START 직후 수집 공백 대비, 아래).
- 갱신 1건이 USER 로 쓰는 asia 호출은 실측 약 9회다 (첫 방문 약 22회 · 재방문 약 3.6회). 한 경기에 실험실 소환사 여럿이 들어가 받을 매치 상당수가 이미 DB 에 있어서, 첫 방문이 계산값 42회보다 적다. 예산 초당 45회 ÷ 9 ≈ 초당 5건이 유저만으로 예산을 다 쓰는 지점이다.
- 그래서 초당 4건(예산의 약 80%)까지는 p95 가 0.6초대이고, 초당 5건부터는 수요가 예산을 넘어 큐가 계속 자란다. FIFO 라 밀린 일이 모든 새 갱신 앞에 서므로 대부분이 기한을 넘긴다. 중간 없이 무너지므로 Plan 2 의 접수 거절은 예산의 80% 안팎에서 끊어야 한다.
- 이 값은 첫 방문 비율에 따라 달라진다. 운영의 갱신 1건당 asia 호출 수를 재서 `45 × 0.8 ÷ (그 값)` 으로 다시 잡는다.
- 초당 3건 실행은 판정이 "429 4건 · 갱신 1080건" 으로 잘못 나왔다. START 직전 몇 분 동안 Prometheus 에 지표가 비어 있어서였다 (스냅숏 복원 중 실험실이 버벅였다). 판정의 증가량 식은 START 에 없던 시리즈를 처음 생긴 것으로 보고 누적값 전체를 센다. 429 4건은 11-04 옛 리미터 때의 누적값이고, 창 안에서 늘지 않았다. 갱신 1080건은 초당 1·2건 실행까지 합친 누적이다. 위 표의 초당 3건 값은 부하 시작 15초 뒤 기준으로 다시 계산한 것이다.

### 걸린 시간 (2026-11-02, Apple Silicon 로컬)

| 단계 | 시간 |
|---|---|
| `docker compose up -d --build` (2026-11-08, 컨테이너·이미지 없는 상태) | 104초 (이미지 안 Gradle 빌드 70초 포함). 이미지가 있으면 `down` 뒤 `up -d` 16초 |
| seed 전체 | 약 330초 (create 12~13초, warm 요청 300초, 큐 비우기 10~11초, 결과 확인 1초 미만, 스냅숏 4초) |
| seed 전체 (2026-11-08, 앱 컨테이너 · ZSET 리미터) | 1170초 (create 12초, warm 요청 301초, 큐 비우기 846초, 스냅숏 11초). 큐 비우기가 길어진 것은 추가 매치 찾기(`renewal.match.find.queue`)가 백필 상한 초당 40회로 빠지기 때문이다. seed 가 가짜 서버 한도를 올려도 이 상한은 앱 쪽 고정값이다 |
| run steady (기본값) | 약 7분 (복원·대기 20~40초 + 부하 5분 + 60초 + 판정) |
| run steady (`RATE=10 DURATION=1m`) | 약 3분 |
| run burst (기본값, 2026-11-05) | 약 6.5분 (391·397초. 복원·대기 + 부하 약 4.5분 + 60초 + 판정) |
| run burst backlog 변형 (`BURST_INTERVAL=15 BURST_ROUNDS=20`, 2026-11-05) | 약 6.5분 (385초. 부하 5분 + 60초 + 판정) |

## 포트

| 포트 | 대상 | 비고 |
|---|---|---|
| 3000 | Grafana | 로그인 없음 (127.0.0.1 에만 연다) |
| 9090 | Prometheus | 1초 수집, 보존 2일, remote write 수신 |
| 8099 | riot-mock | 조작 API `/admin/*`, 지표 `/actuator/prometheus` |
| 9121 | redis_exporter | |
| 8111 | 앱 (컨테이너 `lol-lab-app`) | 공개 포트. `/actuator/prometheus` 를 Prometheus 가 compose 네트워크 안에서 `app:8111` 로 수집 |
| 8112 | 앱 내부 포트 | `/internal/**` |
| 15672 | RabbitMQ 관리 화면 | |
| 15692 | RabbitMQ 지표 | compose 네트워크 안에서만 연다 (호스트에 공개하지 않는다). 확인은 Prometheus 질의로 한다 |

앱·Grafana·Prometheus·riot-mock·redis_exporter 는 인증이 없어 `127.0.0.1` 에만 공개한다.

postgres·rabbitmq·redis 는 `docker/docker-compose.yml` 의 정의를 `extends` 로 가져오고(kafka 는 빼고), 실험실 compose 가 healthcheck 와 postgres 볼륨만 더한다. 컨테이너 이름(`lol-postgres` 등)과 프로젝트가 같아서, 기본 compose 로 같은 서비스를 다시 올리면 설정이 달라 컨테이너가 재생성된다. postgres 데이터는 이름 있는 볼륨이라 실험실 compose 로 돌아오면 다시 붙는다. 15692 는 공개하지 않는다.

## 확인된 동작

- 앱이 뜨면 Prometheus `lol-app`·`riot-mock`·`rabbitmq`·`redis` 4개 job 이 모두 `up` 이다.
- 대시보드는 Grafana 홈이다 (`/api/dashboards/home` 이 실험실 대시보드로 보낸다).
- 요청이 한 번도 없으면 패널 1·2·3 은 모두 0 으로 보인다 (패널 2·3 은 `noValue` 설정).
- `RIOT_API_KEY` 가 환경에 있으면 `LabIsolationGuard` 가 `riot.api-key` 를 이름만 대고 기동을 막는다. 값은 로그에 나오지 않는다.
- `dev,lab`·`prod,lab` 처럼 dev·prod 와 lab 을 같이 켜면 `LabIsolationGuard` 가 빈 생성 전에 `riot.mock.base-url` 을 이름만 대고 막는다 (DB·Redis 연결 전).
- seed 뒤 Redis DB 1 에는 `riot:rl:limits` 가 생기고, DB 0 의 `riot:rl:*` 키 수(0)와 vhost `/` 큐 메시지 수(모두 0)는 seed 전과 같다.
- run 이 앱을 띄운 채 `lol_lab` 을 바꿔도 Hikari 가 끊긴 연결을 버리고("Failed to validate connection") 새로 붙어 health 가 UP 이다. 두 번째 run 도 FAIL 없이 끝났다.
- 같은 스냅숏에서 `run steady` 를 두 번 돌린 총 Riot 호출은 19173 · 19266 (0.5% 차이)이다. seed 를 새로 떠서 돌린 세 번째는 19279 다.
- seed 는 `riot.summoner` 가 4000명인 `lol_lab` 에서 아무것도 바꾸지 않고 종료 1 이고, 앱이 Flyway 를 돌리기 전(테이블 없음)에도 종료 1 이다. 빈 `lol_lab` 에서는 결과 확인(`소환사 4000명, 첫 갱신 success 1500건, failed·skipped 0건`)을 통과해야 스냅숏을 뜬다.
- 가짜 서버는 routing 의 첫 요청 때 `riot_mock_rate_limited_total{type=application|method}`·`riot_mock_unknown_total` 을 0 으로 만든다. 그래서 `increase()` 도 429 가 처음 몰린 창을 센다 (`limit 30:10` 뒤 kr 로 40건: 패널 1 식·옛 `increase()` 식 모두 약 10).
- 패널 1 은 창 안에서 새로 생긴 시리즈를 `X unless X offset 5m` 로 그대로 더한다. 가짜 서버가 처음 보는 routing 에 첫 1초 안에 429 가 몰리면(첫 수집 전에 값이 오른다) `increase()` 만으로는 0 이 된다 (같은 시험을 새 routing `sea` 로 하면 옛 식은 sea 를 0 으로, 패널 1 식은 10 으로 셌다). 5분이 지나면 0 으로 돌아온다.
- 실행 중 k6 표본은 Prometheus 에 1초 안에 들어오고, 대시보드는 1초마다 새로 그린다.

## k6 지표 (Prometheus remote write)

부하 스크립트(seed·steady)만 `lab.sh` 가 `-o experimental-prometheus-rw` 로 지표를 보낸다. 판정 `verify.js` 는 보내지 않는다.
k6 는 늘 `lab.sh` 로 돌린다. `lab.sh` 가 앱 주소(`-e APP_URL`, 위 "명령" 의 변수 표)와 큐 이름(`-e LAB_QUEUES`)을 넘기고, `lib/lab.js` 는 `APP_URL` 이 없으면 바로 멈춘다.
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
| `eclipse-temurin` (앱·riot-mock) | `21.0.11_10-jre@sha256:d2b9f8f12212cadcfdf889461531784e8fd097feade954d65b31ee7a71c473ec` (JRE 21.0.11+10. 같은 버전 태그도 OS 패치로 다시 빌드되므로 검증한 이미지를 다이제스트로 고정) |
| `eclipse-temurin` (이미지 안 빌드) | `21.0.11_10-jdk@sha256:98155457fcdbeb0987472e8abf721657b698ede407aad52ac24ae42618c0931c` |
| `postgres` / `rabbitmq` / `redis` | 기본 `docker/docker-compose.yml` 그대로 (`init-db` 는 postgres, `init-vhost` 는 rabbitmq 이미지를 같이 쓴다) |

## 앱 컨테이너 설정

- 이미지는 `docker/lab/Dockerfile` 이 저장소 루트를 context 로 소스에서 빌드한다 (target `app`·`riot-mock` 이 build 단계를 같이 쓴다). Gradle 캐시는 BuildKit 캐시에 남아, 두 번째부터는 바뀐 모듈만 다시 컴파일한다. context 에서 빠지는 파일은 루트 `.dockerignore` 를 따른다.
- 앱 healthcheck 는 `/actuator/health` 다 (`docker compose ps` 의 `healthy`). run 이 `lol_lab` 을 바꾸는 동안 잠깐 unhealthy 가 될 수 있다.
- 환경변수는 `compose.yml` 의 `app.environment` 뿐이다: `SPRING_PROFILES_ACTIVE=local,lab`, `DB_HOST=postgres`, `REDIS_HOST=redis`, `RABBITMQ_HOST=rabbitmq`, `RIOT_MOCK_BASE_URL=http://riot-mock:8099`, `TZ=Asia/Seoul`, `JAVA_OPTS`. 계정·키는 `application-lab.yml` 기본값(postgres/1234, guest/guest, `lab-key`)이다.
- 루트 `.env` 는 컨테이너에 들어가지 않는다. 그래서 아래 "`.env` 키 충돌" 은 `bootRun` 으로 직접 띄울 때만 해당한다.
- 호스트에서 `bootRun` 으로 띄우던 때와 다른 점: JIT 가 C1 만이 아니고(`bootRun` 은 `-XX:TieredStopAtLevel=1`), 힙 상한이 `-Xmx3g` 이며, 네트워크가 compose 네트워크 안이다. 이전 실행의 수치와 그대로 비교하지 않는다.

## `.env` 키 충돌 (`bootRun` 으로 직접 띄울 때)

`bootRun` 은 루트 `.env` 의 모든 줄을 환경변수로 넣고, 이것이 yml 값을 덮는다. lab 은 격리 값이 어긋나면 기동에 실패한다.
앱 컨테이너(`lab.sh up`·`app`)에는 해당하지 않는다.

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
