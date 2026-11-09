# Riot 호출 허가를 호출하는 쪽으로 옮기고 매치를 묶음으로 받기

- 날짜: 2026-11-09
- 브랜치: `feat/riot-permit-check` (`feat/sliding-rate-limiter` 위, PR base `feat/sliding-rate-limiter`) **[판단]**
- 대체하는 것: `2026-11-05-sliding-rate-limiter-design.md` 4.4 "기다림과 포기 (인터셉터)" 와 인터셉터 체인. ZSET 규칙·안전 비율·백필 상한·유저 활동 중 낮춤·정지·한도 학습은 이어받는다.
- 표시: **[사용자]** 사용자가 정한 것, **[판단]** 작성자가 정한 것 (빌더가 더 나은 근거가 있으면 바꿔도 되지만 이유를 남긴다), **[빌더]** 빌더에게 맡긴 것.

## 1. 왜 바꾸나

- **클라이언트가 기다리고 다시 보낸다.** `RiotRateLimitInterceptor` 는 허가가 날 때까지 호출한 스레드를 재운다 (USER 10초, DEFERRED 60초, BACKGROUND 30초). `RetryInterceptor` 는 429·5xx 를 그 자리에서 최대 3번 다시 보낸다. 한도가 차면 리스너 스레드와 꺼내 온 메시지(un-acked)가 쌓이고, 자원 관리가 클라이언트 안에 숨는다.
- **매치 단계는 메시지 하나의 비용을 모른다.** 매치 목록 1회 + 새 매치마다 match·timeline 2회라서 0~41회로 들쭉날쭉하다. 몇 칸이 필요한지 처리 전에는 모르니 앞에서 한도를 확인할 수 없다.
- **타임라인이 무겁다.** 유저 경로 호출의 절반이고, DB 쓰기(`participant_frame`)의 대부분이다.
- **갱신 코드가 여러 차례 고치며 늘었다.** Lua 10개(265줄), `RenewalMatchStep` 314줄, `RedisRiotRateLimiter` 332줄, 인터셉터 둘 463줄.

## 2. 목표 · 비목표 · 안티목표

**목표**
1. 모든 Riot 호출은 보내기 전에, 호출하는 쪽이 Lua 로 허가를 받는다. 클라이언트에는 한도·재시도 로직이 없다.
2. 허가는 n 건을 한 번에 묻는다. 안 되면 절반씩 줄여 두 번 더 묻는다.
3. 유저 갱신의 타임라인은 날짜순 최근 3게임만 받고, 나머지는 백그라운드에서 받는다.
4. 매치·타임라인 저장은 지금처럼 묶음 한 번이다. 큐 메시지에는 id 만 싣는다.
5. 갱신 코드와 Lua 를 1차로 정리한다.

**비목표**
- 한도 규칙 자체(ZSET 슬라이딩 창, 안전 비율 0.9, 백필 초당 40, 유저 활동 중 낮춤 DEFERRED 0.8 · BACKGROUND 0.5)는 바꾸지 않는다.
- 갱신 상태 모델(레코드, 단계 비트 1·2·4·8, 기한 5초, 늦은 완료)은 바꾸지 않는다.
- RabbitMQ 지연 큐(TTL + DLX) 도입은 다음으로 미룬다. "1초 뒤 다시 넣기" 는 지금처럼 리스너가 1초 자고 넣는다.

**안티목표** (이렇게 되면 동작해도 실패다)
- 허가 없이 나가는 Riot 호출이 하나라도 있다 (재시도 포함).
- 허가를 기다리며 리스너 스레드가 잠든다. (거절된 메시지를 1초 쉬고 다시 넣는 것은 허가를 기다리는 것이 아니라 다시 넣는 속도를 늦추는 것이다. 지연 큐가 생길 때까지의 예외로 둔다 — 비목표 3번째.)
- 매치 단위로 저장(트랜잭션)이 쪼개진다.

## 3. 사용자가 정한 것 [사용자]

1. 클라이언트의 한도 인터셉터를 없애고, 리스너나 `RiotApiService` 를 부르기 전에 Lua 로 확인한다.
2. n 건을 한 번에 확인하고, 얻지 못하면 절반씩 줄여 두 번 더 시도한다 (20 → 10 → 5).
3. 5건도 자리가 없으면 남은 매치를 매치 적재 큐(유저 재시도 순위)로 넘기고, 완료 판정기가 마무리한다.
4. 타임라인은 날짜순 3게임만 유저 경로에서 받고, 나머지는 백그라운드에서 적재한다.
5. 트랜잭션을 하나씩 타지 않고, 네트워크 전송을 최소로 줄인다.
6. 갱신 관련 소스를 1차로 정리하고, Lua 스크립트를 단순하게 관리한다.
7. 최근 3게임 타임라인은 갱신 성공 조건에 넣지 않는다 ("되면 좋은 것", 2026-11-09 선택 (나)).

## 4. 허가

### 4.1 모양 [판단]

`module:common` 의 `com.mmrtr.lol.support.riot` 에 둔다 (지금 `RiotCallPriority` 가 있는 곳). 도메인 서비스도 쓰기 때문이다.

```java
public interface RiotPermitPort {
    /** platform 의 routing 안에서 method 별 건수를 한 번에 묻는다. 전부 허가하거나 아무것도 기록하지 않는다. 기다리지 않는다. */
    Optional<RiotPermits> tryAcquire(String platform, RiotCallPriority priority, Map<RiotMethod, Integer> counts);
}

public final class RiotPermits {          // 허가받은 토큰 묶음
    RiotPermit take(RiotMethod method);   // 쓸 때마다 하나씩 꺼낸다. 남는 토큰은 버려도 된다 (4.6)
}

public record RiotPermit(String token, String platform, RiotMethod method, RiotCallPriority priority) {}

public enum RiotMethod {                  // 이름 = 지금 RiotApiService 가 쓰는 method 문자열
    ACCOUNT_BY_RIOT_ID("account-v1.by-riot-id", REGIONAL), ACCOUNT_BY_PUUID(...), SUMMONER_BY_PUUID(..., PLATFORM),
    LEAGUE_ENTRIES_BY_PUUID(...), MATCH_IDS_BY_PUUID(..., REGIONAL), MATCH(...), MATCH_TIMELINE(...), ...;
}
```

- app 한도는 routing 마다 따로라서 Lua 한 번은 한 routing 만 다룬다. `tryAcquire` 에 두 routing 의 method 가 섞여 오면 routing 마다 Lua 를 차례로 부른다 **[판단]**.
  - 뒤 routing 이 거절되면 `Optional.empty()` 를 돌려준다. 앞 routing 에서 받은 토큰은 지우지 않는다 (4.6 처럼 보수적인 낭비).
  - 보내기 **전에** 전부 받으므로, 실제 호출을 낭비하지 않고 토큰만 낭비한다.
  - 예: 공개 Riot ID 조회(`SummonerApiAdapter.fetchSummonerByRiotId`)와 랭킹 스케줄러의 `SummonerService.getSummonerByPuuid` 는 account(regional) + summoner·league(platform) 3건이다. 한 번의 `tryAcquire` 로 다 받고 시작한다.
- 갱신 매치 묶음은 match·timeline 이라 같은 routing 이다.
- 거절이면 `Optional.empty()` 다. 대기 시간을 돌려주지 않는다. 기다리는 쪽이 없기 때문이다.
- 지금의 `RiotRateLimiter`·`RedisRiotRateLimiter` 가 이 포트를 구현한다. `complete`·`learnLimits`·`pause` 는 riot-client 안에서만 쓴다.

### 4.2 절반씩 줄여 묻기 [사용자 + 판단]

목록(예: 대상 매치)과 "항목 몇 개가 method 별로 몇 건인가" 를 받아 아래처럼 돈다. 헬퍼 하나에 둔다. 지금은 갱신 매치 단계만 쓴다 **[판단]**.

1. 시도 크기는 `n`, `⌈n/2⌉`, `⌈n/4⌉` 이다. n 은 남은 항목 수(최대 시작 크기로 자른 값)다. 같은 크기가 겹치면 한 번만 묻는다 (n = 1 이면 한 번). **[사용자: 20 → 10 → 5]**
2. 시도 사이에 기다리지 않는다. 큰 묶음이 지금 빈 칸에 안 맞을 뿐 작은 묶음은 맞을 수 있기 때문이다. **[판단]**
3. 한 크기가 허가되면 그 항목들을 보낸다. 남은 항목이 있으면 1부터 다시 돈다 (시작 크기 = 남은 수). **[판단]**
4. 세 번 모두 거절되면 남은 항목을 돌려준다. 그 뒤는 호출하는 쪽의 "다른 조치" 다 (4.5).

항목이 앞에서부터 잘린다. 갱신 매치는 최신순이므로 최신 매치부터 받는다.

### 4.3 Lua (`riot-permit.lua`, 지금 `riot-acquire.lua` 를 바꾼다) [판단]

지금 구조(1단계 모두 검사 → 2단계 모두 기록)는 그대로 두고, **허가/거절만** 돌려준다.

- 입력
  - KEYS: 정지 키들(app + 묶음의 각 method), 유저 활동 키, 규칙 ZSET 들
  - ARGV: batchId, now(빈 값이면 Redis TIME), 우선순위 level, 낮춤 비율, 유저 활동 ms, method 별 건수, 규칙마다 (상한, 창 ms, 낮춤 여부, 대상 method — 비면 app 규칙)
- 규칙마다 필요한 칸 = app 규칙이면 건수 합, method 규칙이면 그 method 건수, 백필 전용 규칙(초당 40)이면 건수 합.
- 1단계: 정지 키가 하나라도 살아 있으면 거절. 규칙마다 창 밖 기록을 지우고 `ZCARD + 필요한 칸 > 실효 상한` 이면 거절. 아무것도 기록하지 않는다.
- 2단계: 모두 통과하면 토큰을 기록한다. 토큰 이름은 `{batchId}:{method}:{k}` 이고, app 규칙에는 모든 토큰, method 규칙에는 그 method 토큰을 넣는다.
- 반환: 1 허가, 0 거절.
- 없어지는 것: 대기 ms 계산, "낮춘 상한만 막혔으면 유저 활동 끝까지만 기다리게" 분기. 둘 다 기다리는 쪽이 있어야 의미가 있었다.
- 그대로인 것: USER 요청이 유저 활동 표시를 켜는 것(거절돼도 켠다), 유저 활동 중 DEFERRED·BACKGROUND 의 낮춘 상한, 백필 전용 규칙(USER 가 아닐 때만).
- 필요한 칸이 상한보다 큰 묶음은 늘 거절된다. 절반 줄이기로 작아지므로 따로 다루지 않는다.

### 4.4 보내고 받기 (`RiotApiService`) [판단]

- 모든 메서드(그리고 그것을 감싼 도메인 port)가 `RiotPermit` 을 인자로 받는다. **허가 없이는 부를 수 없게 타입으로 막는다.** 메서드는 permit 의 method 가 자기 것과 다르면 `IllegalArgumentException` 을 던진다.
- 우선순위는 permit 에서 읽는다. `RiotCallScope`·`RiotRetryScope`·`RiotRequestAttributes` 는 없앤다.
- 응답을 받으면 지금 인터셉터의 응답 쪽 일을 그대로 한다:
  1. `complete(permit)` — 토큰 기록을 응답 시각으로 옮긴다 (`riot-complete.lua`, 응답마다 하나). 허가 시각은 Riot 이 받은 시각보다 앞서므로 옮겨야 안전하다.
  2. 응답 헤더로 한도를 배운다 (`learnLimits`).
  3. 한도 근처면 잠깐 정지한다 (`NEAR_LIMIT_PAUSE`).
- 헤더가 필요하므로 `.retrieve().body(...)` 대신 `.toEntity(...)` 로 받는다.
- 429: `complete` 하고 `Retry-After`·`X-Rate-Limit-Type` 으로 정지한 뒤 `RiotRateLimitException` 을 던진다. **다시 보내지 않는다.**
- 5xx·IO·시간 초과: `complete` 하고 예외를 던진다. **다시 보내지 않는다.** 재시도는 호출하는 쪽이 메시지 단위로 한다.
- `RiotRateLimitInterceptor`·`RetryInterceptor` 를 없앤다. 응답 지표 `riot.api.responses{status,host}` 는 `RiotApiService`(또는 로그 인터셉터)로 옮겨 남긴다. `riot.api.retry.*` 지표는 사라진다.

### 4.5 호출하는 쪽마다 거절됐을 때 할 일

| 호출하는 쪽 | 우선순위 | 한 번에 묻는 건수 | 거절되면 |
|---|---|---|---|
| 갱신 소환사·계정·리그 단계 | USER | 1 | 메시지를 1초 뒤 다시 넣는다. 시도 수를 올리지 않는다 (지금 "예약 실패" 처리와 같다) **[판단]** |
| 갱신 매치 단계 — 매치 목록 | USER | 1 | 위와 같다 |
| 갱신 매치 단계 — 매치·타임라인 | USER | 남은 대상 ≤ 20 (+ 타임라인 ≤ 3), 절반 줄이기 | 남은 매치를 매치 적재 큐에 유저 재시도 순위로 넘기고, 완료 판정기가 마무리한다 **[사용자]** |
| 매치 적재 큐 (`MatchDataProcessor`) — 매치 | 순위대로 (USER_RETRY = USER, BACKFILL = BACKGROUND) | match + timeline = 2 | 1초 쉬고 다시 넣는다 **[판단]** |
| 매치 적재 큐 — 나중 타임라인 (6장) | `TIMELINE_LATER` = DEFERRED | timeline = 1 | 1초 쉬고 다시 넣는다 **[판단]** |
| 추가 매치 찾기 (`MatchFindService`) | 지금 우선순위 | 1 | 메시지를 1초 뒤 다시 넣는다 **[판단]** |
| 공개 API (Riot ID 조회, 관전) | USER | 1 | 기다리지 않고 "잠시 후 다시" 오류로 응답한다 **[판단]**, 오류 종류 **[빌더]** |
| 스케줄러 (티어 수집, 랭킹 계산, 챔피언 로테이션) | BACKGROUND | 1 (랭킹 계산은 3, 4.1) | 스케줄러 스레드에서 잠깐 기다렸다 다시 묻거나 다음 주기로 미룬다 **[빌더]** |

매치 적재 큐는 지금 혼잡(`QUEUE_FULL`)일 때 그 자리에서 `basicNack(requeue=true)` 한다. 지금은 인터셉터가 10~60초 기다린 뒤에야 거절하니 괜찮았다. 이제는 거절이 바로 나므로, 그대로 두면 같은 메시지가 쉬지 않고 돈다. 그래서 다른 리스너처럼 1초 쉬고 다시 넣는다 **[판단]**.

### 4.6 쓰지 못한 토큰

허가만 받고 보내지 못한 토큰(앞 호출 실패로 멈춤 등)은 지우지 않는다. 창이 끝날 때까지 자리를 차지하지만 실제보다 많이 세는 쪽이라 안전하다 **[판단]**.

## 5. 갱신 매치 단계

1. 매치 목록 허가(1건) → 최근 매치 id 20개 (Riot 응답이 최신순).
2. **대상** = DB 에 없는 매치. **타임라인 대상** = 최신 3게임 중 타임라인이 DB 에 없는 것 **[사용자: 날짜순 3게임 / 판단: 매치는 있는데 타임라인만 없는 경우도 포함, 4번째로 채우지 않음]**.
3. 대상을 레코드에 기록하고 선점한다 (지금 그대로).
4. 4.2 로 묶음 허가 → 허가된 묶음마다 match ∥ timeline 을 병렬로 받는다 (타임라인은 그 묶음에 든 타임라인 대상만).
5. 받은 것을 **모았다가 한 번에 저장**한다: `SaveMatchDataUseCase.execute(matches, timelines)` (timelines ≤ 3).
   - 저장 쪽은 타임라인을 matchId 로 짝짓는다 (`TimeLineService.saveAll`). 확인함.
   - 단, 그 짝은 **같은 호출로 저장하는 매치**에서만 찾는다 (`matchEntityMap.get(matchId)`). 매치가 이미 DB 에 있고 타임라인만 받은 경우는 지금 저장되지 않는다. 그래서 "타임라인만 저장" 하는 길이 필요하다 (6장과 같이 쓴다).
6. 이번에 저장한 매치 중 타임라인을 받지 않은 것은 매치 적재 큐에 **나중 타임라인 순위(`TIMELINE_LATER`)** 로 넣는다 (6장). 메시지에는 id 만 싣는다.
7. 4.2 에서 세 번 다 거절돼 남은 것은 이렇게 넘긴다.
   - **매치 대상**(DB 에 없는 매치): 매치 적재 큐에 유저 재시도 순위(`USER_RETRY`)로 넘긴다 **[사용자]**. 그 길은 match·timeline 을 둘 다 받는다 (지금 그대로) **[판단]**.
   - **타임라인만 필요한 대상**(매치는 이미 DB 에 있음): `USER_RETRY` 로 넘기면 안 된다. `MatchDataProcessor` 가 DB 에 있는 매치를 `DUPLICATE` 로 버리기 때문이다. 그래서 `TIMELINE_LATER` 로 넘긴다 **[판단]**.
   - **허가받은 묶음 안에서 호출 하나가 실패한 경우** **[판단]** (인라인 재시도가 없으니 지금보다 잦아진다):

     | 실패 | 처리 |
     |---|---|
     | match 4xx (429 제외) | 실패 표시를 남긴다 (지금 그대로) |
     | match 의 그 밖 실패 (429·5xx·IO·시간 초과) | 매치를 통째로 `USER_RETRY` 로 넘긴다 (지금 그대로) |
     | match 는 받고 timeline 만 실패 | 매치는 저장하고, 타임라인만 `TIMELINE_LATER` 로 넘긴다 (새로) |
8. MATCH 비트: **대상 매치가 모두 저장되면** 이 단계가 켠다. 넘긴 것이 있으면 완료 판정기가 DB 를 보고 켠다. 판정기는 지금처럼 매치 존재만 본다.
   - 최근 3게임 타임라인은 성공 조건이 아니라 "되면 좋은 것" 이다 **[사용자: (나)]**. 대개 같은 묶음에서 함께 저장되지만, 넘겨졌거나 다른 스레드에서 늦게 저장되면 SUCCESS 뒤에 들어온다.
   - 그래서 갱신 상태 모델(레코드·비트·판정기)은 바꾸지 않는다 (비목표와 맞음).
9. 20개가 꽉 차면 21번째부터를 추가 매치 찾기로 넘기는 것은 그대로다.

## 6. 나중 타임라인 (매치 적재 큐의 순위 1)

새 큐를 만들지 않는다. 2026-11-03 설계가 매치 적재 큐 `mmrtr.matchId.priority` 의 처리 순위 1 을 "나중 타임라인" 자리로 비워 두었다. 코드에도 `MatchQueueRank.TIMELINE_LATER(rank 1, AMQP priority 3, DEFERRED)` 가 쓰이지 않은 채 있다. 이번에 그 자리를 쓴다 **[이전 결정을 따름]**.

- **순서와 몫:** 같은 큐 안에서 유저 재시도·백필보다 먼저 꺼낸다. 우선순위가 DEFERRED 라 백필 전용 상한(BACKGROUND 의 초당 40)에 묶이지 않는다. 유저 활동 중에는 낮춘 상한(0.8)만 받는다.
- **메시지:** matchId 하나 + platform. 매치 적재 큐의 메시지 형식을 그대로 쓴다.
- **처리 (`MatchDataProcessor` 에 순위별 분기를 더한다):**
  1. 타임라인이 이미 DB 에 있으면 `DUPLICATE` 로 ack 한다.
  2. 허가 1건(MATCH_TIMELINE, DEFERRED) → 거절이면 1초 쉬고 다시 넣는다.
  3. 타임라인만 받는다.
  4. `MatchBatchProcessor` 로 저장한다. 1초마다 모아 쓰므로 트랜잭션이 메시지마다 쪼개지지 않는다 **[사용자: 트랜잭션 하나씩 X]**. `MatchBatchProcessor` 에 "타임라인만" 항목을 넣는 길이 필요하다 (5장 5번의 "타임라인만 저장" 과 같은 저장 메서드).
- **메시지 수:** 갱신 1건에서 최대 17건이다. 메시지에는 id 만 실리므로 전송량은 작다.
- **늦게 보이는 시간:** 실험실에서 재서 기록한다. 판정 기준은 두지 않는다 **[판단]**.

## 7. 갱신 코드 · Lua 정리 (1차)

### 7.1 Lua: 10개 → 9개, 모두 같은 형식

모든 스크립트 머리 주석을 같은 형식으로 맞춘다: 한 줄 목적 / KEYS / ARGV / 반환. TTL 같은 숫자는 스크립트에 쓰지 않고 Java 가 넘긴다 (지금도 그렇다).

| 지금 | 바뀐 뒤 | 단순해지는 것 |
|---|---|---|
| `riot-acquire.lua` (95줄) | `riot-permit.lua` | n 건 기록. 대기 ms 계산과 "낮춘 상한만 막힘" 분기 삭제. 허가/거절만 |
| `riot-complete.lua` | 그대로 | |
| `riot-pause.lua` | 그대로 | |
| `renewal-open.lua` | 대상 set 키를 지우는 줄이 빠진다 | |
| `renewal-field.lua` + `renewal-targets.lua` | `renewal-update.lua` 하나 | renewalId 가 같을 때만 필드 여러 개를 HSET. 대상 매치를 레코드의 필드 하나(쉼표 구분)로 둔다 → 대상 set 키, 그 TTL 을 레코드에 맞추던 코드, `targetsReady` 필드(= 대상 필드가 있으면 준비됨)가 없어진다 |
| `renewal-mark.lua` | KEYS 에서 대상 set 키가 빠진다 | |
| `renewal-fail.lua` | KEYS 에서 대상 set 키가 빠진다 | |
| `claim-release.lua`, `claim-mark-failed.lua` | 그대로 | |

대상 매치는 최대 20개라 필드 하나로 충분하다. 이 브랜치들은 아직 운영에 나가지 않았으므로 옛 키 형식과의 호환은 두지 않는다 **[판단]**.

### 7.2 Java

- **없앤다:** `RiotRateLimitInterceptor`, `RetryInterceptor`, `RiotRequestAttributes`, `RiotCallScope`, `RiotRetryScope`, `RiotBudgetExceededException` (허가 거절은 예외가 아니라 `Optional.empty()`).
- **`RenewalStepFailures.fetchFailed` 를 단순하게:**
  - 404 → `not_found`
  - 429 · 5xx · IO · 시간 초과 → 시도 수 2까지 다시 넣고, 그 뒤 실패
  - 허가 거절은 예외가 아니므로 여기 오지 않는다. 단계가 직접 1초 뒤 다시 넣는다.
- **`RenewalMatchStep` 을 나눈다:** 대상 계산 / 묶음 받기(4.2) / 저장·넘기기. 클래스 구조는 **[빌더]**.
- **소환사·계정·리그 단계:** "허가 → 조회 → 저장 → 비트" 흐름이 같다. 공통화할지는 **[빌더]**.
- **대상 set 이 없어지는 것에 맞춰 port 를 바꾼다:** `findTargets` → 레코드의 대상 필드.
- **`MatchQueueRank.TIMELINE_LATER`:** 지금 쓰이지 않는 순위를 6장대로 쓰기 시작한다.

### 7.3 문서

- 루트 `README.md` 의 Rate Limiting 절, `docker/lab/README.md` 의 호출 경로·지표 설명, 실험실 Grafana 대시보드에서 `riot.api.retry.*` 를 쓰는 패널을 고친다.
- `2026-11-05-sliding-rate-limiter-design.md` 4.4 에 이 문서가 대체한다고 적는다.
- `CLAUDE.md` 의 데이터 흐름(인터셉터 체인) 문구는 고치지 않고 사용자에게 알린다.

## 8. 검증

**테스트** (Testcontainers Redis, 지금 테스트 구조를 따른다)
- `riot-permit.lua`:
  - n 건 허가 시 규칙마다 맞는 토큰이 기록된다.
  - 규칙 하나라도 넘치면 아무것도 기록하지 않는다.
  - 정지 중이면 거절한다.
  - 유저 활동 중 DEFERRED·BACKGROUND 는 낮춘 상한을 쓴다.
  - 백필 전용 규칙은 USER 에 걸리지 않는다.
  - 필요한 칸이 상한보다 크면 거절한다.
- 절반 줄이기:
  - 20 → 10 → 5 순서로 묻는다.
  - 성공 뒤 남은 것을 이어서 묻는다.
  - 세 번 거절되면 남은 목록을 돌려준다.
  - n = 1 이면 한 번만 묻는다.
- 여러 routing 허가: 두 routing 이 섞이면 routing 마다 묻고, 뒤가 거절되면 빈 값을 돌려준다.
- `MatchDataProcessor`: 거절되면 1초 쉬고 다시 넣는다. `TIMELINE_LATER` 는 타임라인이 있으면 버리고, 없으면 타임라인만 받아 모아 저장한다.
- `RiotApiService`:
  - 응답 뒤 `complete` 한다.
  - 429 면 정지하고 다시 보내지 않는다.
  - 5xx 면 다시 보내지 않는다.
  - permit 의 method 가 다르면 거절한다.
- 매치 단계:
  - 최근 3게임 타임라인만 받는다.
  - 저장은 1회다.
  - 타임라인을 받지 않은 매치마다 `TIMELINE_LATER` 메시지가 1건씩 나간다.
  - match 는 받고 timeline 만 실패하면, 매치는 저장되고 타임라인만 `TIMELINE_LATER` 로 넘어간다.
  - 세 번 거절된 타임라인만 필요한 대상은 `TIMELINE_LATER` 로 넘어간다 (`USER_RETRY` 가 아니다).
  - 세 번 거절되면 남은 매치를 매치 적재 큐로 넘긴다.
  - 이미 있는 매치의 타임라인만 받는 경우가 저장된다.

**타입:** 허가 없이 Riot 을 부르는 코드가 컴파일되지 않는다.

**실험실** (`docker/lab`)
- steady 초당 4건, 3분: 429 0건, 3초 안 99% 이상.
- 초당 5·6건에서 처리 한계가 얼마나 올랐는지 재서 기록한다 (기준이 아니라 기록).
- run 이 끝난 뒤 나중 타임라인이 모두 들어오는지, 얼마나 걸리는지 재서 기록한다.

**`./gradlew check`** 통과.

## 9. 위험과 한계

- **거절이 잦으면 메시지가 1초마다 돈다.** 리스너가 1초 잔 뒤 다시 넣는 지금 방식 그대로이고, 지연 큐는 다음 일이다.
- **붐빌 때 공개 API 조회가 바로 실패한다.** 지금은 최대 10초 기다리지만, 이제는 기다리지 않고 "잠시 후 다시" 로 응답한다.
- **최근 3게임 밖의 타임라인은 화면에 늦게 보인다.**
- **타임라인 없이 SUCCESS 가 될 수 있다 (받아들인 것).** 최근 3게임 타임라인이 넘겨졌거나 늦게 저장되면, 갱신 직후 화면에 타임라인이 잠깐 비어 보인다 **[사용자: (나)]**.
- **묶음 허가는 칸이 한꺼번에 비어야 한다.** 단건 요청이 많으면 큰 묶음이 거절되기 쉽지만, 절반 줄이기와 넘기기로 막히지는 않는다.
- **지난번 과측정 원인은 그대로 남는다.** 1.286 과측정(2026-11-05 설계 7장)은 이번 변경과 관계없다.

## 10. 빌더에게 맡기는 것

클래스 이름·패키지, 절반 줄이기 헬퍼의 위치와 모양, 공개 API 오류 종류, 스케줄러가 기다리는 방식, 단계 공통화 여부, `MatchBatchProcessor` 에 타임라인만 항목을 넣는 모양, 테스트 파일 구성.

## 11. 순서 (계획에서 단계로 나눈다)

1. **허가로 바꾸기.** 허가 포트, `riot-permit.lua`, `RiotApiService` 응답 처리, 인터셉터 제거, 모든 호출하는 쪽에 허가 붙이기. 매치 단계는 이 단계에서는 지금처럼 전부 match + timeline 을 받는다.
2. **매치 단계.** 최근 3게임 타임라인, 절반 줄이기, 넘기기, 타임라인만 저장, 나중 타임라인(`TIMELINE_LATER`) 처리.
3. **갱신 Lua · 코드 정리** (7장).
