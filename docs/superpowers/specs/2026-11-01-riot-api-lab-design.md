# Riot API 운영 실험실 (로컬) 설계

- 작성일: 2026-11-01
- 범위: lol-repository — 가짜 Riot 서버 도구, 앱 실험실 프로파일, 앱 지표, 로컬 관측 스택, 부하 시나리오
- 상태: 설계 (구현 전)
- 표기: **[결정]** 은 사용자가 정한 것, **[판단]** 은 설계 작성자가 정한 것이다. 빌더가 더 나은 근거가 있으면 [판단] 은 바꿀 수 있다.
- 관련: `2026-10-27-summoner-renewal-pipeline-design.md` (갱신 파이프라인 재설계), PR #130 (20게임 이후 매치 저장), PR #135 (Riot 호출 한도 관리자)

## 1. 왜 만드나

### 컨셉

- 이 프로젝트는 **한도가 500건/10초인 외부 API(Riot)를 안정적으로 운영하는 서비스**다. **[결정]**
- 로컬에서 시험한다. **[결정]**
- 요청이 어떻게 흘러가는지와 큐 메시지 상태를 실시간으로 본다. **[결정]**
- 목표 부하는 갱신 요청 초당 10건, 기준은 "Riot 429 가 나지 않는다" 이다. **[결정 — 이전 설계]**

### 지금 로컬에서 할 수 없는 이유

- **500/10초를 재현할 키가 없다.**
  - 개인·개발 키 한도는 20/1초·100/2분이다 (`riot-local.yml`).
  - 운영 키는 운영 파드에서만 쓴다. **[결정 — 이전 설계]**
- **수집·화면이 없다.**
  - 앱은 `/actuator/prometheus` 를 노출하지만 로컬에는 그걸 모아 보여 줄 곳이 없다.
  - 운영 모니터링(lol-deploy 의 kube-prometheus-stack)도 lol-repository 는 아직 수집하지 않는다 (`k8s/helm/app/templates/servicemonitor.yaml` 주석).
- **한 화면에 모아 볼 수 없다.** RabbitMQ 관리 화면(15672)은 큐 수치만 보여 준다. Riot 호출·한도 상태와 나란히 볼 수 없다.
- **부하 전에 소환사가 있어야 한다.** 갱신은 DB 에 있는 소환사만 받는다 (`SummonerRenewalRequestService` 의 `NOT_FOUND_PUUID`).
- **develop 이 빌드되지 않는다.**
  - springdoc 3.1.1(#128)이 Spring Boot 4 를 요구해 `:module:infra:api` 컴파일이 실패한다.
  - 그래서 앱을 띄울 수 없다.

## 2. 목표 · 비목표 · 안티목표

### 목표

- **G1. 실험실을 명령 하나로 띄운다.** 가짜 Riot 서버·인프라·관측 스택을 함께 띄우고, 앱은 프로파일 하나로 붙인다.
- **G2. 초당 10건 갱신 부하에서 429 = 0 을 확인한다.**
  - 기준은 가짜 Riot 서버가 낸 application·method 429 다.
  - 대시보드와 판정 명령(`lab.sh verify`) 양쪽으로 확인한다.
- **G3. 요청 흐름과 큐 상태가 변화 후 5초 안에 화면에 보인다.**
  - 요청 흐름: 갱신 접수 → 큐 → 리스너 → 한도 관리자 → Riot → 저장.
  - 큐 상태: 대기·처리 중·소비자·재시도·DLQ.
- **G4. 운영에서 생길 일을 시나리오로 재현한다.** 같은 명령으로 몇 번이고 다시 돌릴 수 있어야 한다.
  - Riot 한도 변경
  - 순간 폭주
  - 백그라운드 수집과 겹침
  - Riot 장애
  - 앱 인스턴스 2대
- **G5. 새 앱 지표는 운영에서도 쓸모가 있다.** 가짜 서버 쪽 지표만 실험실 전용이다.

### 비목표

- 운영 대시보드·ServiceMonitor 반영 (lol-deploy). 후속 작업으로 한다.
- 로그 수집(Loki)을 로컬에 구성하는 것.
- lol-server·lol-ui 를 실험실에 넣는 것. 사용자 트래픽은 부하 도구가 흉내 낸다.
- 실제 Riot 응답을 녹화해 재생하는 것.
- 처리량 개선 자체. 실험실은 측정 도구다. 갱신 파이프라인 개선은 기존 설계(Plan 2·3)가 한다.

### 안티목표 — 돌아가더라도 실패로 본다

- 가짜 서버가 Riot 보다 느슨해서 "429 = 0" 이 거짓 안심이 된다.
- 실험실 설정이 다른 프로파일로 샌다. 그래서 실제 Riot 으로 가짜 키나 폭주가 나가거나, 실제 키가 가짜 서버에 전달된다.
- 실험실 데이터와 평소 local 데이터(DB·Redis·RabbitMQ)가 서로 섞인다. 예: local 에서 배운 한도 값이 실험을 오염시킨다.
- "실시간" 이라면서 화면이 10초 넘게 늦다.
- 실험실 코드가 운영 jar 에 들어간다.
- 갱신이 느리거나 실패하는 모습을 판정에서 숨긴다. Plan 2 전에는 갱신이 밀리는 게 정상이고, 화면에 그대로 보여야 한다 (5.6).

## 3. 제약

- Java 21, Spring Boot 3.5.x, Gradle 멀티모듈(`settings.gradle`).
- Java 를 바꾸면 `./gradlew check`(Checkstyle + 테스트)를 통과해야 한다 (CLAUDE.md 7장).
- macOS + Docker Desktop 이다. 컨테이너에서 호스트로는 `host.docker.internal` 로 간다.
- 앱 로컬 포트는 공개 8111, 내부 8112 다 (`api-local.yml`). `bootRun` 은 `spring.profiles.active` 만 JVM 에 넘기고 루트 `.env` 를 읽는다.
- **Riot 주소와 routing 값**
  - Riot 주소는 `Platform` enum 에 `https://{routing}.api.riotgames.com` 형태로 박혀 있다.
  - 한도 관리자는 요청 호스트의 첫 라벨을 routing 값으로 쓴다 (`RiotRateLimitInterceptor.routingOf`).
- **쿨다운:** 소환사별 갱신은 2분(`Summoner.RENEWAL_INTERVAL`), 클릭은 10초(`CLICK_COOLDOWN_TTL`).
- **갱신 1건의 Riot 호출 수는 그 소환사가 처음인지에 따라 10배 넘게 다르다.**
  - Riot ID 조회로 막 만든 소환사(`initSummoner()`)는 revisionDate 를 2026-01-01 로 두고 매치를 저장하지 않는다.
  - 그래서 첫 갱신은 새 게임 20개를 받는다. asia 를 약 42회 부르고(#135 `MatchDataFetcher`: match 20 + timeline 20 + 계정·ID 목록), 그 뒤 백필(`MatchFindListener` → `mmrtr.matchId`)이 BACKGROUND 로 약 80회를 더 부른다.
  - 이미 갱신한 적 있는 소환사의 재갱신은 새 게임 수에 따라 0~8회다.
- **실패한 갱신도 큐에서는 성공처럼 보인다.**
  - USER 예약이 10초 범위를 넘으면 `RiotBudgetExceededException` 이 나고 그 갱신은 통째로 실패한다.
  - 그런데 `SummonerRenewalListener` 는 예외를 삼키고 ack 한다. 그래서 큐 지표만으로는 실패를 알 수 없다. 갱신 결과는 따로 세야 한다 (5.3).
- **ArchUnit:** 도메인 모델 패키지(`domain/`)는 인프라를 모른다. 지표 코드는 어댑터·API·application 층에 둔다.
- **선행 조건**
  - #130(매치 리스너 켬)과 #135(새 한도 관리자·지표)가 develop 에 머지돼 있어야 한다.
  - springdoc 빌드 깨짐이 고쳐져 있어야 한다.
- `docs/superpowers/plans/` 는 `.gitignore` 대상이다. 계획 파일은 로컬 worktree 에만 있다.
- **노트북 자원:** 앱 2대와 인프라·관측 스택이 한 노트북에서 돌아야 한다. 앱 컨테이너 힙 상한은 768MB 정도로 잡는다. **[판단]**

## 4. 전체 구조

```
k6 (사용자 흉내) ──HTTP──▶ lol-repository 앱  [local,lab 프로파일 · 1대 또는 2대]
                              │  갱신 접수 → RabbitMQ(vhost lab) → 리스너 → 한도 관리자(Redis db 1)
                              │  Riot 요청 주소는 그대로 두고, 보내기 직전에 목적지만 가짜 서버로
                              ▼
                         riot-mock  (가짜 Riot: 500/10초 강제 + 심판 지표)

Prometheus (1초 수집) ◀── 앱 · riot-mock · RabbitMQ · Redis · k6(remote write)
Grafana (1초 새로고침) ── "Riot API 운영 실험실" 대시보드
Tempo (3단계)          ◀── 앱 trace (OTLP)
```

실행 방식은 두 가지다. **[판단]**

- **개발 모드:** 인프라·관측·가짜 서버는 Docker 로 띄우고, 앱 1대를 호스트에서 `bootRun` 한다. 코드를 고치며 빠르게 돌리는 용도다.
- **분산 모드:** 앱 2대를 같은 이미지로 Docker 에 띄운다. 두 대가 같은 Redis·RabbitMQ 를 쓰면서 Riot 예산을 나눠 쓰는지 본다.

## 5. 구성 요소

### 5.1 가짜 Riot 서버 `riot-mock` [판단]

가짜 서버는 **심판**이다. 판정이 믿을 만하려면 Riot 만큼 엄격하거나 그보다 더 엄격해야 한다 (안티목표 1).

**위치와 주소**

- Gradle 하위 프로젝트 `:tools:riot-mock` 로 만든다. 별도 bootJar 를 가진 Spring Boot 앱이고, Docker 이미지로도 빌드한다.
  - `module:app:application` 은 이 프로젝트에 의존하지 않는다. 그래서 운영 jar 에 들어가지 않는다.
- 주소 형태는 `http://{호스트}:8099/{routing}/{Riot 원래 경로}` 다. 예: `/asia/lol/match/v5/matches/KR_1`.
- `RiotApiService` 가 부르는 엔드포인트를 모두 지원한다.
  - 모르는 경로는 404 로 답하고 `riot_mock_unknown_total{path}` 를 올린다. 빠진 엔드포인트가 화면에 드러난다.
- `X-Riot-Token` 이 없으면 401 로 답한다. 토큰 값은 로그·지표에 남기지 않는다.

**한도 강제 (Riot 방식)**

- **한도 단위**
  - application 한도: routing 별. 기본값 `500:10`. **[결정 — 500/10 컨셉]**
  - method 한도: routing × 엔드포인트별. 기본값은 아래 표.
  - 둘 다 여러 창을 받는다 (`"500:10,30000:600"` 형식).
- **창 방식**
  - Riot 처럼 고정 창이다. 버킷의 첫 요청 시각에 창이 열리고 W초 뒤 닫힌다.
  - 429 로 거절된 요청도 그 창의 수에 넣는다. 덜 관대한 쪽을 고른 것이다.
- **초과 시:** 429 와 함께 `Retry-After`(창이 닫힐 때까지 남은 초, 올림)와 `X-Rate-Limit-Type: application|method` 를 준다.
- **응답 헤더:** 200·404·429 모든 응답에 `X-App-Rate-Limit`, `X-App-Rate-Limit-Count`, `X-Method-Rate-Limit`, `X-Method-Rate-Limit-Count` 를 Riot 과 같은 형식(`500:10`, `37:10`)으로 붙인다.

method 한도 기본값은 임의값이다. 운영 키의 실제 값이 아니므로 Riot 개발자 포털 값으로 바꿔도 된다. application 한도가 가장 빡빡하도록 넉넉하게 잡되, 티어 수집 경로 하나만 일부러 좁혀서 method 버킷이 걸리는 모습도 볼 수 있게 한다.

| 엔드포인트 | 기본 method 한도 |
|---|---|
| account-v1 (by-riot-id, by-puuid) | `1000:60` |
| summoner-v4 by-puuid | `1600:60` |
| league-v4 entries by-puuid | `20000:10` |
| league-exp-v4 entries (티어 수집) | `50:10` |
| match-v5 ids by-puuid · match · timeline | 각 `2000:10` |
| 그 밖의 엔드포인트 | `1000:10` |

**심판 지표 — 가짜 서버만 아는 사실**

- `riot_mock_requests_total{routing,endpoint,status}`
- `riot_mock_rate_limited_total{routing,type}`: G2 판정 기준이다.
- `riot_mock_window_count{bucket}`, `riot_mock_window_limit{bucket}`: 지금 창의 사용량과 한도.
- `riot_mock_sliding_peak{routing,window}`
  - 지난 아무 W초 구간(100ms 단위로 미는 창)의 최대 호출 수다.
  - 실제 Riot 창이 언제 열리는지는 알 수 없다. 그래서 이 값도 한도 미만이어야 "안전" 으로 본다.
  - 이것이 고정 창보다 엄격한 두 번째 기준이다.

**지연·장애 흉내**

- 엔드포인트별로 평균±흔들림 지연을 준다. 기본값은 다음과 같고, 이 지연만 기본으로 켜져 있다.
  - account·summoner·league: 60±30ms
  - match: 120±60ms
  - timeline: 200±100ms
- 다음 장애는 기본으로 꺼져 있다.
  - 5xx 비율
  - service 429 비율 (`X-Rate-Limit-Type: service`, `Retry-After` 없음)
  - 지연 급증 (확률, 추가 ms)

**조작 API — Riot 이 한도를 바꾸는 상황 재현용**

- `GET`/`PUT /admin/limits`: application·method 한도를 실행 중에 바꾼다. 다음 창부터 적용한다.
- `PUT /admin/chaos`: 장애 설정을 바꾼다.
- `POST /admin/reset`: 창·피크·카운터를 초기화한다.

**데이터 생성 — 결정적, 저장소 없음**

- **소환사:** Riot ID `lab{n}#LAB`(n = 1..4000)이다. puuid 는 n 에서 결정적으로 만든 78자 문자열이라 같은 n 이면 항상 같은 값이 나온다. n 범위로 두 풀을 나눈다 (5.6).
- **리그:** 여러 티어에 고르게 나눠 준다.
- **매치 ID 목록**
  - 기본 기록은 소환사당 60개다 (최근 20 + 백필 40).
  - 새 게임은 벽시계가 아니라 **실행(epoch) 단위**로 정한다.
    - `POST /admin/reset` 이 epoch 를 하나 올린다.
    - 재방문 풀 소환사 n 은 epoch e 에서 새 게임 K(n, e)개를 더 가진다. K 는 (n, e)에서 결정적으로 뽑는다.
    - K 기본 분포는 0개 30%, 1개 40%, 2개 20%, 3개 10% 다. 운영 측정값이 아니라 임의값이다. 운영 로그(`[갱신-API] fetchNewMatchIds count=`)로 분포를 재면 그 값으로 바꾼다.
    - 같은 실행 안에서는 몇 번을 불러도 같은 목록이 나온다. 그래서 실행마다 부하가 같고, Plan 2 전후를 같은 조건으로 비교할 수 있다.
    - epoch 0 에서는 모든 소환사의 K 가 0 이다. seed 는 epoch 0 에서 돈다.
  - summoner-v4 의 `revisionDate` 는 그 소환사의 가장 최근 게임 끝 시각이다.
    - 앱은 revisionDate 가 그대로면 첫 호출에서 갱신을 끝낸다.
    - 그래서 새 게임이 0개인 재갱신은 Riot 을 1번만 부른다.
- **매치·타임라인 JSON**
  - 앱의 readmodel DTO 를 그대로 직렬화해 만든다. 그래서 앱이 반드시 읽을 수 있다.
  - 값은 그럴듯해야 한다: 큐 420/440, 현재 패치의 `gameVersion`, 실제 챔피언 ID 범위 등. 저장·통계 경로에서 거부되면 안 된다.
- **함께 뛴 매치:** 일부 매치는 lab 소환사 10명이 함께 뛴 것으로 만든다. 그래서 중복 처리 경로(`match:processing`, `DUPLICATE`)가 실제처럼 돈다.
- **티어 수집:** league-exp 페이지(티어 수집용)도 lab puuid 로 채운다.

**테스트**

- 창 계산, 헤더 형식, `Retry-After` 를 단위 테스트한다.
- 계약 테스트: 앱의 `RateLimitWindow.parse` 가 가짜 서버 헤더를 그대로 읽는지 확인한다.

### 5.2 앱 실험실 프로파일 `lab` [판단]

**실행:** `-Dspring.profiles.active=local,lab`. local 설정 위에 lab 설정을 덮어쓴다.

**목적지 바꾸기**

- `riot.mock.base-url` 이 있으면 `RiotApiConfig` 의 request factory 를 감싼다.
- 감싼 factory 는 `https://{sub}.api.riotgames.com{path}?{query}` 를 `{base-url}/{sub}{path}?{query}` 로 보낸다.
- interceptor 체인(재시도 → 한도 → 로그)은 원래 주소를 그대로 본다.
  - Spring 은 interceptor 를 다 지난 뒤에 `requestFactory.createRequest(uri, …)` 를 부른다.
  - 그래서 routing·method·지표 태그가 운영과 똑같다.

**안전장치 — 안티목표 2**

- `riot.mock.base-url` 이 설정됐는데 활성 프로파일에 `prod` 나 `dev` 가 있으면 기동에 실패한다.
- 기동 시 WARN 로그 한 줄을 남긴다: "Riot 요청을 가짜 서버(…)로 보냅니다".
- lab 설정의 API 키는 `lab-key` 다. 실제 키가 가짜 서버로 가지 않는다.

**격리 — 안티목표 3**

- 실험실은 따로 쓴다: Postgres DB `lol_lab` (Flyway 가 `riot` 스키마를 만든다), Redis DB 1, RabbitMQ vhost `lab`.
- 지금은 Redis DB 번호와 vhost 를 설정으로 바꿀 수 없다.
  - `RedissonConfig` 와 `RabbitMqConfig.connectionFactory()` 가 연결을 직접 만들면서 `spring.data.redis.database`·`spring.rabbitmq.virtual-host` 를 읽지 않는다.
  - 두 키를 읽게 고친다. 기본값은 지금과 같은 0 과 `/` 다.
- **격리 확인:** lab 프로파일로 뜰 때 다음을 확인하고, 하나라도 다르면 기동에 실패한다.
  - datasource URL 이 `lol_lab` 을 가리킨다.
  - Redis DB 가 1 이다.
  - vhost 가 `lab` 이다.
  - Riot API 키가 `lab-key` 다.
  - 이렇게 확인하는 이유: 루트 `.env` 의 환경변수(예: `SPRING_DATASOURCE_URL`)는 프로파일 yml 보다 우선한다. 그래서 설정 파일만 믿으면 평소 local DB 로 실험이 돌 수 있다.
- **lab 표시:** `/actuator/info` 에 `lab: true` 를 노출한다. `lab.sh` 는 이걸 확인한 뒤에만 부하를 준다.
- `lab.sh reset` 으로 통째로 지운다.

**한도 설정과 지표**

- `riot.rate-limit.app-default: "500:10"`. local 의 개발 키 기본값(`20:1,100:120`)을 덮어쓴다.
- 1초 수집에 맞춰, 주요 Timer 에 percentile 히스토그램을 켠다.

### 5.3 앱 지표 추가 [판단]

**이미 있는 것:** `riot.api.responses{status,host}`, `riot.api.retry.*`, `riot.ratelimit.wait/rejected/near_limit`, `riot.api.rate_limited`, `cache.match.write`.

**추가하는 것**

이름과 태그는 빌더가 정한다. 운영에서도 의미가 있어야 한다 (G5).

- **Riot 호출:** `riot.api.calls{routing,method,priority,outcome}`.
  - 한도 interceptor 에서 시도 1회마다 1씩 올린다.
  - 우선순위별 초당 호출 수가 흐름 화면의 중심이다.
- **한도 관리자 상태**
  - Redis 에서 읽는 gauge 이고, 수집할 때 최대 1초 캐시한다.
  - 앱이 배운 한도 `riot.ratelimit.limit{bucket,window}`
  - 지금 초 사용량 `riot.ratelimit.used{routing}`
  - 정지 중 여부 `riot.ratelimit.paused{bucket}`
  - 가짜 서버 한도를 바꾸면 앱이 따라가는 모습이 여기서 보인다.
- **갱신**
  - 요청 결과 `lol.renewal.requests{result=accepted|in_progress|cooldown|not_renewable}`
  - 처리 결과 `lol.renewal.completed{result=success|failed,reason}`. reason 은 예외 종류(예: `budget_exceeded`, `riot_5xx`, `timeout`)다.
    - 리스너가 실패를 삼키고 ack 하므로 이 지표가 없으면 실패가 안 보인다 (3장).
    - 그래서 이것만은 1단계에 넣는다.
  - 접수부터 완료까지 걸린 시간 `lol.renewal.duration`
  - 진행 중 갱신 수
- **큐**
  - 발행 `lol.mq.published{queue}`
  - 처리 결과 `lol.mq.consumed{queue,outcome=ack|requeue|reject}`
  - Spring AMQP 기본 `spring.rabbitmq.listener` 타이머도 함께 쓴다.
- **매치 저장:** 배치 큐 크기 gauge, flush 건수·시간, `DUPLICATE`·`QUEUE_FULL` 횟수.

**태그 규칙:** 값이 유한한 것만 태그로 쓴다. puuid·matchId 를 태그로 쓰지 않는다.

### 5.4 관측 스택 [판단]

**파일 위치:** `docker/lab/` 아래에 둔다.
- `compose.yml`
- `prometheus/prometheus.yml`
- `grafana/provisioning/…`, `grafana/dashboards/*.json`
- `k6/*.js`
- `lab.sh`

기존 `docker/docker-compose.yml`(postgres·redis·rabbitmq)은 그대로 둔다. 실험실은 `-f` 두 개로 합쳐 같은 compose 프로젝트로 띄운다.

**Prometheus**

- 1초마다 수집한다. k6 결과를 받으려고 `--web.enable-remote-write-receiver` 를 켠다.
- 수집 대상
  - 앱: 개발 모드는 `host.docker.internal:8111`, 분산 모드는 `app-1`·`app-2`.
  - riot-mock
  - RabbitMQ `:15692/metrics/per-object` (큐별 수치)
  - redis_exporter

**Grafana**

- 익명 관리자로 접속하고 `min_refresh_interval = 1s` 로 둔다.
- 데이터소스와 대시보드는 파일로 프로비저닝한다. 운영 lol-deploy 처럼 대시보드는 git 이 소유한다. 그래야 나중에 lol-deploy 로 옮기기 쉽다.
- 기본 홈 화면은 실험실 대시보드다.

**RabbitMQ:** 3.13 이미지의 prometheus 플러그인을 쓴다. `lab.sh up` 이 `rabbitmqctl` 로 vhost `lab` 과 권한을 만들고, 플러그인이 꺼져 있으면 켠다.

**포트**

| 대상 | 포트 |
|---|---|
| Grafana | 3000 |
| Prometheus | 9090 |
| riot-mock | 8099 |
| RabbitMQ 지표 | 15692 |
| redis_exporter | 9121 |
| Tempo | 3200, OTLP 4318 |
| 분산 모드 앱 | 8121, 8122 |

### 5.5 대시보드 "Riot API 운영 실험실" [판단]

판정 → 흐름 → 원인 순서로 위에서 아래로 놓는다.

1. **판정** (stat 패널)
   - 가짜 서버 429 (최근 5분). 0 이면 초록.
   - Riot 초당 호출 수 vs 예산 45·한도 50
   - 10초 구간 최대 사용률 (sliding peak / 500)
   - 초당 갱신 요청 수
   - 갱신 완료 p95
   - 큐 적체 합계와 예상 소진 시간
2. **요청 흐름** (Grafana Canvas 패널)
   - 위쪽 흐름: k6 → 갱신 API → `mmrtr.summoner` → 갱신 리스너 → 한도 관리자(USER / DEFERRED / BACKGROUND) → riot-mock.
   - 아래쪽 흐름: 갱신 리스너 → `renewal.match.find.queue` / `mmrtr.matchId` → 매치 처리 → 배치 큐 → DB.
   - 화살표에는 초당 처리량, 큐 상자에는 대기·처리 중·소비자 수를 실시간으로 적는다.
3. **Riot 한도**
   - 우선순위별 초당 호출 (누적 그래프 + 예산선)
   - 버킷별 창 사용량(가짜 서버) vs 앱이 배운 한도
   - 우선순위별 한도 대기 p50/p95
   - 양보·거절·정지
4. **큐**
   - 큐별 대기·처리 중 추이
   - 발행·소비·ack·requeue·reject 비율
   - DLQ 깊이
   - 소비자 수
5. **저장:** 초당 매치 처리, 배치 큐 크기, flush 시간, 중복.
6. **부하 (k6):** 가상 사용자 수, 초당 요청, 응답 시간, 실패.
7. **인스턴스**
   - 인스턴스별 Riot 호출. 분산 모드에서 두 대가 예산을 나눠 쓰는 모습이 보인다.
   - JVM 스레드·힙

기본 시간 범위는 최근 5분, 새로고침은 1초다. 대시보드 위에 RabbitMQ 관리 화면 링크를 둔다.

갱신 파이프라인 Plan 2 가 들어오면 진행률(10/20/30/40)·접수 거절 패널을 이 대시보드에 더한다. 그 작업은 Plan 2 쪽에서 한다.

### 5.6 부하와 시나리오 [판단]

**부하 도구**

- k6 를 Docker(`grafana/k6`)로 돌린다.
- 결과는 Prometheus remote write 로 보내 같은 대시보드에서 본다.

**소환사 풀 두 개**

갱신 1건의 Riot 호출 수는 첫 갱신이냐 재갱신이냐에 따라 10배 넘게 다르다 (3장). 그래서 풀을 나눈다.

| 풀 | 범위 | 시작 상태 | 갱신 1건의 Riot 호출 |
|---|---|---|---|
| 첫 방문 | `lab1` … `lab2500` | 소환사만 있고 매치 없음 | USER 약 42 + 백필 BACKGROUND 약 80 |
| 재방문 | `lab2501` … `lab4000` | 한 번 갱신을 마쳐 기록 60개 저장 | 새 게임 K개에 따라 0~8 |

- 부하는 두 풀을 정해진 비율로 섞는다. 기본은 첫 방문 30% · 재방문 70% 다.
  - 운영 측정값이 아니라 임의값이다.
  - 운영 분포를 재면 그 값으로 바꾼다 (5.1 의 K 와 같은 출처).
- 소환사는 풀마다 순서대로 고른다. 첫 방문 풀은 한 실행 안에서 한 번씩만 쓴다. 재방문 풀은 2분 쿨다운이 지난 뒤에만 다시 쓴다.
- 풀 크기 근거:
  - 첫 방문 2500: `steady` 5분 × 10건/초 × 30% = 900, `spike` 최대 약 600 에 여유를 더했다.
  - 재방문 1500: `spike` 30초 동안 50건/초 × 70% = 1050 을 쿨다운에 걸리지 않고 받으려면 필요하다.

**실행마다 같은 시작 상태**

같은 명령이면 몇 번을 돌려도 같은 부하여야 한다. 그래야 Plan 2 전후를 비교할 수 있다.

- **처음 한 번만: `lab.sh seed`**
  1. 가짜 서버 한도를 잠시 크게 올린다 (예: `100000:10`). seed 는 Riot 예산을 시험하는 단계가 아니라서 빨리 끝나게 한다.
  2. 두 풀의 Riot ID 를 모두 앱의 Riot ID 조회 API 로 조회해 소환사를 만든다. 실제 경로를 탄다.
  3. 재방문 풀은 한 번씩 갱신하고, 백필까지 끝날 때(큐가 빌 때)를 기다린다.
  4. 가짜 서버 한도를 `500:10` 으로 되돌린다.
  5. `lol_lab` DB 를 템플릿 DB(`lol_lab_snapshot`)로 복사해 둔다. `pg_dump` 보다 빠르다.
- **실행할 때마다: `lab.sh run <시나리오>`**
  1. 앱 연결을 끊고 `lol_lab` 을 지운 뒤 `lol_lab_snapshot` 에서 다시 만든다. 앱의 커넥션 풀은 다시 연결한다.
  2. Redis DB 1 을 비운다. 앱이 seed 동안 배운 큰 한도 값도 이때 지워진다.
  3. 실험실 vhost 의 큐를 모두 비운다.
  4. 가짜 서버를 reset 해 epoch 를 올린다.
  5. 부하를 준다.
  6. 판정한다.
- 시작 상태는 "DB 스냅숏 + 빈 Redis DB 1 + 빈 큐 + 새 epoch" 다. 앱은 다시 띄우지 않아도 된다.
  - 단, 앱 메모리의 학습 한도 캐시는 다음 응답 헤더로 곧 바로잡힌다.
  - 그래도 첫 1초에 `app-default`(`500:10`)보다 많이 나가지는 않는다.

**사용자 흉내 한 번**

1. 갱신 `POST` 를 보낸다.
2. 접수되면 상태 `GET` 을 1초, 1.5초, 2초… 간격으로 최대 10초까지 폴링한다 (lol-ui 흉내).

**판정 단계**

- **FAIL — 안전 기준.** 어느 시나리오든 하나라도 어기면 실패다.
  - 가짜 서버 application·method 429 = 0
  - sliding peak < 한도 (모든 창)
- **WARN — 처리 기준.**
  - 숫자와 함께 눈에 띄게 보여 주지만, Plan 2 전에는 실패로 치지 않는다.
  - 갱신 실패 수와 이유, 갱신 시간, 큐 소진 시간이 여기 들어간다.
- **실패를 숨기지 않는다 (안티목표 6).**
  - 지금 구조에서는 첫 갱신 비율이 조금만 높아도 USER 수요가 예산을 넘는다. 그러면 갱신이 `budget_exceeded` 로 실패하고, 큐는 "처리됨"으로 비워진다.
  - verify 는 큐가 빈 이유가 성공인지 실패인지를 `lol.renewal.completed` 로 나눠 보여 준다.
- Plan 2 가 들어오면 "3초 안에 40" 같은 처리 기준을 FAIL 로 올린다. 그건 Plan 2 쪽 일이다.

**시나리오**

| 이름 | 부하 | FAIL 기준 | WARN 로 보여 줄 것 |
|---|---|---|---|
| `steady` | 10건/초, 5분, 기본 비율 | 안전 기준 | 갱신 성공·실패(이유별), p95 시간, 큐별 적체 |
| `spike` | 2건/초 → 50건/초 30초 → 2건/초, 기본 비율 | 안전 기준 | 세 큐(`mmrtr.summoner`, `renewal.match.find.queue`, `mmrtr.matchId`)가 폭주 뒤 비기까지 걸린 시간(5분 넘으면 WARN), 갱신 실패 수 |
| `mixed` | 재방문만 5건/초 + 60초 뒤 티어 수집(BACKGROUND) 시작 | 안전 기준 | USER 한도 대기 p95 를 앞 60초(기준선)와 혼잡 구간으로 나눠 비교. 혼잡 구간이 기준선의 2배 + 200ms 를 넘으면 WARN |
| `limit-change` | `steady` 도중 `500:10` → `300:10` → `500:10` | 바꾼 직후 창 1개(10초)를 뺀 구간에서 안전 기준 | 바꾼 직후 창의 429 수, 앱이 배운 한도가 따라가기까지 걸린 시간(30초 넘으면 WARN) |
| `chaos` | `steady` + 5xx 5% + service 429 1% + 지연 급증 | application·method 429 = 0, sliding peak | 재시도 성공률, service 429 수 |
| `cluster` | 앱 2대 + `steady` | 안전 기준 | 인스턴스별 호출 비중 |

- `mixed` 는 재방문 풀만 쓴다. 그래서 USER 수요(약 25회/초)가 예산 45 안에 들어간다.
  - USER 만으로 예산이 차면 두 구간 모두 대기가 예약 상한(10초) 근처에 붙는다. 그러면 비교가 뜻이 없어진다.
  - 이 시나리오는 "BACKGROUND 가 USER 를 늦추지 않는다"만 본다.
- `limit-change` 의 바꾼 직후 창을 빼는 이유:
  - Riot 이 한도를 낮추면, 앱은 다음 응답 헤더를 받아야 알 수 있다.
  - 이미 그 창에서 새 한도보다 많이 보냈다면 429 를 피할 방법이 없다.
  - 운영에서도 같다.

**명령:** `lab.sh verify <시나리오>` 는 Prometheus API 로 위 기준을 계산하고, FAIL/WARN/PASS 표와 종료 코드(FAIL 이면 1)를 낸다.

### 5.7 `lab.sh` 명령 [판단]

| 명령 | 하는 일 |
|---|---|
| `up` / `down` | 인프라 + 관측 + 가짜 서버 |
| `app` | 개발 모드 앱 실행 (`bootRun`, `local,lab`) |
| `cluster up` / `cluster down` | 분산 모드 앱 2대 |
| `seed` | 두 풀 준비 + 재방문 풀 갱신 + DB 스냅숏 (처음 한 번, 5.6) |
| `load <시나리오>` | 부하만 |
| `verify <시나리오>` | 판정만 |
| `run <시나리오>` | 스냅숏 복원 → Redis DB 1·큐 비우기 → 가짜 서버 reset → 부하 → 판정 |
| `limit <한도>` | 가짜 서버 application 한도 변경 (모든 routing 에 같이 적용) |
| `chaos <프리셋>` | 장애 프리셋 적용 |
| `reset` | 실험실 DB·vhost·Redis DB 1·가짜 서버 초기화 |
| `urls` | 화면 주소 출력 |

### 5.8 단일 요청 추적 (3단계, 뺄 수 있음) [판단]

- Micrometer Tracing(OTel bridge) + OTLP 로 Tempo 에 보내고, Grafana 에서 trace 를 본다. lab 에서만 켜고 샘플링은 100% 다.
- **이어져야 하는 구간:** 갱신 API → RabbitMQ 발행 → 리스너 → Riot 호출 → 매치 큐 → 매치 처리 → 저장.
  - Riot 호출의 한도 대기는 별도 span 으로 둔다.
- **비동기 경계에서 문맥이 끊기지 않게 해야 한다.**
  - 이 레포는 RabbitMQ 컨테이너 팩토리와 `RabbitTemplate` 을 직접 만든다. 그래서 observation 을 직접 켜야 한다.
  - `CompletableFuture` 실행기는 context-propagation 으로 감싸야 한다.
- **운영:** 기본은 꺼짐이다. 켤지는 따로 정한다.
- 이 작업이 커지면 별도 계획으로 뺀다.

## 6. 단계와 순서 [판단]

0. **선행**
   - springdoc 빌드 수정 PR (2.8.x 로 내림). 다른 PR 과 상관없이 바로 할 수 있다.
   - #130·#135 를 develop 에 머지.
   - **실험실 구현은 두 PR 이 머지된 뒤 develop 에서 시작한다. 그 브랜치 위에 쌓지 않는다.**
     - 실험실은 #135 의 Redis 키 구조(`riot:rl:limits`, `riot:rl:c:{bucket}:{W}:{idx}`, `riot:rl:pause:*`), 지표 이름(`riot.ratelimit.*`, `riot.api.rate_limited`), `RateLimitWindow.parse` 에 직접 기댄다.
     - 리뷰에서 이것들이 바뀌면 gauge 와 계약 테스트를 다시 해야 한다.
     - 머지 전에 꼭 시작해야 하면 #135 브랜치 위에 쌓는다. #135 가 바뀌면 rebase 하지 않고 merge 로 따라간다 (force-push 금지).
1. **1단계 "실험실 기반"** → G1·G2 확인
   - riot-mock 과 테스트
   - lab 프로파일: 목적지 바꾸기, 안전장치, 격리
   - 관측 스택
   - `seed`(스냅숏 포함)·`run steady`·`verify`
   - 갱신 결과 지표 `lol.renewal.completed` 하나 (5.3). 실패가 안 보이면 판정을 믿을 수 없기 때문이다.
   - 최소 대시보드: 판정 행, 큐 깊이, 우선순위 없는 초당 호출, 갱신 성공·실패. 이미 있는 지표, 가짜 서버·RabbitMQ 지표, 위 지표 하나로 그린다.
2. **2단계 "흐름·큐 관측"** → G3·G4
   - 5.3 의 앱 지표 추가
   - 전체 대시보드 (Canvas 흐름 포함)
   - 나머지 시나리오, 분산 모드
3. **3단계 "단일 요청 추적"** — 선택.

단계마다 PR 을 하나씩 낸다. 그 다음 기존 갱신 파이프라인 Plan 2·3 을 실험실 안에서 진행해 전후를 비교한다.

## 7. 빌더에게 맡기는 것

- 정확한 지표 이름·태그, 패널 쿼리, Canvas 배치.
- riot-mock 웹 스택. 권장은 Spring MVC + 가상 스레드다.
- 데이터 생성의 세부 값. 단, 앱 저장 경로에서 거부되지 않아야 한다.
- 실험실 compose 를 기존 compose 와 합치는 방식의 세부.
- 분산 모드 앱 이미지 빌드. 권장은 기존 `docker/Dockerfile` 을 쓰고 프로파일만 바꾸는 것이다.
- seed 에 쓸 Riot ID 조회 API 선택. 조건은 소환사를 DB 에 저장하는 쪽이어야 한다는 것이다.

## 8. 검증

- **riot-mock:** 창·헤더·`Retry-After` 단위 테스트, 앱 파서 계약 테스트.
- **앱**
  - 목적지 바꾸기 테스트: interceptor 는 원래 주소를 보고, 실제 요청은 가짜 서버로 간다.
  - 안전장치 테스트: `prod` + `base-url` 이면 기동에 실패한다.
  - 새 지표 테스트.
- **실험실 전체**
  - 1단계 완료 기준: `lab.sh run steady` 에 FAIL 이 없다.
  - 2단계 완료 기준: 여섯 시나리오 모두 FAIL 이 없다. WARN 은 수치와 함께 PR 에 적는다 (Plan 2 전에는 WARN 이 나오는 게 정상일 수 있다).
  - 같은 시나리오를 두 번 돌렸을 때 Riot 호출 총수가 비슷해야 한다 (시작 상태 재현 확인).
- **Java 변경마다** `./gradlew check`.
