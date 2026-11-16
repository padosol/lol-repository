# Riot 한도: 허가 Lua 하나와 429 정지만 남기기

- 날짜: 2026-11-11
- 브랜치: `feat/riot-permit-check` 에 이어서 **[판단]**
- 대체하는 것: `2026-11-09-riot-permit-check-design.md` 4.3 의 반환값(1/0)과 4.4 의 응답 쪽 일 1~3(`complete`·`learnLimits`·한도 근접 정지), `2026-11-05-sliding-rate-limiter-design.md` 의 한도 학습·응답 시각 옮기기·정지 Lua. 허가를 호출하는 쪽, 절반 줄이기, 거절 처리(11-09 4.1·4.2·4.5·4.6)는 그대로다.
- 표시: **[사용자]** 사용자가 정한 것, **[판단]** 작성자가 정한 것 (빌더가 더 나은 근거가 있으면 바꿔도 되지만 이유를 남긴다), **[빌더]** 빌더에게 맡긴 것.

## 1. 왜 바꾸나

지금 Riot 한도 관리는 네 가지 일을 한다.

| 일 | 어디서 | Redis 왕복 |
|---|---|---|
| 허가 | `riot-permit.lua` | 허가마다 routing 당 1 |
| 응답 뒤 토큰 시각 옮기기 | `riot-complete.lua` | **응답마다** 1 |
| 헤더로 한도 배우기 | `learnLimits` → `riot:rl:limits` 해시 HSET, 인스턴스 캐시, 10초마다 다시 읽기 | 바뀔 때·30초마다 1, 다시 읽을 때 1 |
| 정지 | `riot-pause.lua` (429, Count 헤더 95% 근접) | 429·근접마다 1 |

- 응답 쪽 세 가지가 허가보다 코드와 Redis 왕복이 많고, 따라가기 어렵다 (`RedisRiotRateLimiter` 447줄 중 절반 이상).
- 허가 Lua 가 1/0 만 돌려줘서, 거절이 app 한도 때문인지 method 한도 때문인지 정지 때문인지 지표로 알 수 없다.

## 2. 목표 · 비목표 · 안티목표

**목표**
1. Riot 한도 관리는 허가 Lua 하나와 429 정지 키 하나로 한다.
2. 한도는 설정 파일에 적는다 (app 한도 하나, method 별 한도). 같은 값을 routing(asia·kr …)마다 따로 센다.
3. 허가 Lua 는 정지 → app → method → 백필 순서로 보고, 막힌 곳을 반환값으로 알려 준다.
4. 정상 응답 뒤에는 Redis 를 부르지 않는다. 429 일 때만 SET 한 번 한다.

**비목표**
- 허가를 받는 쪽(리스너·도메인 서비스), 절반 줄이기(20 → 10 → 5), 거절됐을 때 할 일은 바꾸지 않는다.
- 규칙 자체는 바꾸지 않는다: ZSET 슬라이딩 창, 안전 비율 0.9, 유저 활동 중 낮춤(DEFERRED 0.8, BACKGROUND 0.5), 백필 초당 40 **[판단]**. 이것들은 허가 Lua 안의 일이라 "permit 만 남긴다" 에 포함된다고 봤다.
- routing 마다 Lua 를 따로 부르는 구조와, routing 사이에 원자성이 없는 것(앞 routing 토큰을 지우지 않음)은 그대로다.
- `RiotPermitPort` 의 모양(`Optional<RiotPermits>`)과 `RiotPermit`(token 포함)은 바꾸지 않는다 **[판단]**. 호출하는 쪽 수십 곳과 테스트를 건드리지 않기 위해서다.

**안티목표** (이렇게 되면 동작해도 실패다)
- 허가 없이 나가는 Riot 호출이 있다.
- 한 인스턴스가 429 를 받았는데 다른 인스턴스는 계속 보낸다.
- 설정에 한도가 없는 method 가 호출되지 못하거나, 그 때문에 앱이 뜨지 않는다.
- 응답 뒤 일의 실패가 Riot 호출 결과를 바꾼다 (지금처럼 경고 로그만 남긴다).

## 3. 사용자가 정한 것 [사용자]

1. 허가(permit)만 남긴다. 응답 뒤 토큰 시각 옮기기, 헤더로 한도 배우기, 한도 근접 정지는 없앤다. 헤더 한도는 복잡하니 우선 뺀다.
2. 429 정지는 최소 형태로 남긴다.
3. 허가 Lua 는 ZSET 으로 app → method 순서로 남은 칸이 있는지 보고, 없으면 음수(-1 같은 값)를 돌려준다.
4. routing(리전·플랫폼)마다 따로 센다. 지금도 그렇게 되어 있으므로 그대로 둔다.

## 4. 한도 설정 [판단]

```yaml
riot:
  rate-limit:
    app-limits: "500:10,30000:600"
    method-limits:
      # 이름에 점이 있어 대괄호 표기를 쓴다 (없으면 바인더가 중첩 키로 쪼갠다)
      "[account-v1.by-puuid]": "1000:60"
      "[summoner-v4.by-puuid]": "2000:60"
      "[league-v4.entries-by-puuid]": "20000:10,1200000:600"
      "[match-v5.ids-by-puuid]": "2000:10"
      "[match-v5.match]": "2000:10"
      "[match-v5.timeline]": "2000:10"
```

- 형식은 Riot 헤더와 같은 `한도:창초,...` 이고 `RateLimitWindow.parse` 로 읽는다. 기동할 때 한 번 읽어 규칙으로 만들어 둔다.
- 같은 값이 routing 마다 따로 적용된다. `kr` 의 app 500:10 과 `asia` 의 app 500:10 은 다른 ZSET 이다 (키는 지금과 같다: `riot:rl:w:{routing}|app:{창}`, `riot:rl:w:{routing}|m|{method}:{창}`).
- 규칙 상한은 지금처럼 `max(1, floor(한도 × safety-ratio))` 다.
- `method-limits` 에 없는 method 는 method 규칙 없이 app 규칙(과 백필 규칙)만 본다. 기동할 때 한도가 없는 method 를 INFO 로그 한 줄로 알린다.
- 기동을 막는 경우: `app-limits` 가 비었거나 형식이 틀림, `method-limits` 의 이름이 `RiotMethod.methodName()` 에 없음(오타), 값의 형식이 틀림.

**프로파일별 값**

위 값은 지금 쓰는 키로 Riot 응답 헤더에서 배운 값이다 (2026-11-11 로컬 Redis `riot:rl:limits` 에서 확인, kr·asia 같음).

| 프로파일 | app-limits | method-limits |
|---|---|---|
| prod, dev | 위 값 | 위 값 |
| local | 위 값. 로컬 `.env` 의 키가 운영 키 한도를 받았기 때문이다 (위와 같은 확인). 개발 키를 쓰면 `.env` 에 `RIOT_RATELIMIT_APPLIMITS=20:1,100:120` 을 넣는다 | 위 값 |
| lab | `500:10` (지금처럼) | `tools/riot-mock` 의 `method-limits` 와 같게 **[빌더]** |

- 아직 값을 모르는 method: `account-v1.by-riot-id`, `league-v4.entries`, `league-v4.challengerleagues`, `league-v4.grandmasterleagues`, `champion-v3.rotations`, `spectator-v5.active-games`. 빌더가 Riot 개발자 포털이나 실제 응답 헤더로 확인되면 채우고, 확인되지 않으면 비워 둔다 (app 규칙만 본다) **[빌더]**.
- `.env.example` 에 `RIOT_RATELIMIT_APPLIMITS` 를 주석으로 적어 둔다.

**`RiotAPIProperties.RateLimit` 필드**

| 필드 | 처리 |
|---|---|
| `appDefault` | `appLimits` 로 이름을 바꾼다 ("배우기 전 기본값" 이 아니게 됐으므로) |
| `methodLimits` (`Map<String, String>`) | 새로 둔다. 기본값 빈 맵 |
| `safetyRatio`, `userActiveWindow`, `deferredCeilingRatio`, `backgroundCeilingRatio`, `backgroundMaxPerSecond` | 그대로 |
| `defaultPause` | 그대로 (429 에 Retry-After 가 없을 때) |
| `countPauseRatio`, `limitsRefresh` | 지운다 |

## 5. 허가 Lua (`riot-permit.lua`)

한 routing 의 묶음 하나를 본다. 지금의 "1단계 모두 검사 → 2단계 모두 기록" 은 그대로이고, 검사 순서를 정하고 막힌 곳을 돌려주는 것만 바뀐다.

**순서와 반환값** [사용자: app → method, 음수] [판단: 코드 값과 정지·백필 자리]

1. USER 요청이면 유저 활동 표시를 켠다 (거절돼도 켠다, 지금과 같다).
2. 정지 키(app 정지, 묶음의 각 method 정지) 중 하나라도 있으면 **`-3`**. 값은 보지 않고 있는지만 본다 (`EXISTS`). 정지가 끝나는 때는 키 만료가 정한다.
3. app 규칙(창마다): 창 밖 기록을 지우고 `ZCARD + 묶음 전체 건수 > 실효 상한` 이면 **`-1`**.
4. method 규칙(묶음의 method 마다, 창마다): `ZCARD + 그 method 건수 > 실효 상한` 이면 **`-2`**.
5. 백필 규칙(BACKGROUND 일 때만, 초당 40): `ZCARD + 묶음 전체 건수 > 40` 이면 **`-4`**.
6. 모두 통과하면 모든 규칙에 토큰을 기록하고(app·백필 규칙에는 모든 토큰, method 규칙에는 그 method 토큰) 키 만료를 창 길이로 늘린 뒤 **`1`**.

- 실효 상한: app·method 규칙은 유저 활동 중 DEFERRED·BACKGROUND 면 `max(1, floor(상한 × 비율))`, 그 밖에는 상한. 백필 규칙은 낮추지 않는다 (지금과 같다).
- 거절(`-1`~`-4`)이면 아무것도 기록하지 않는다. 앞서 통과한 규칙에도 남기지 않는다.
- 토큰 이름은 지금과 같은 `{batchId}:{method}:{k}` 다.
- 입력(KEYS·ARGV) 배치는 빌더가 정한다 **[빌더]**. 규칙마다 app/method/백필 중 무엇인지 알 수 있어야 3~5 의 순서와 반환값을 낼 수 있다.

**Java 쪽 (`RedisRiotRateLimiter.tryAcquire`)**
- 반환 모양은 그대로다. `1` 이면 토큰 묶음, 음수면 `Optional.empty()`.
- 지표 `riot.permit.requests{routing, priority, result}` 의 result 를 `granted | app | method | paused | background` 로 나눈다 (지금은 `granted | denied`).
- 알 수 없는 반환값은 거절로 보고 result `unknown` 으로 센다 **[판단]**.

## 6. 429 정지 (최소 형태) [사용자 + 판단]

- `RiotCallRecorder` 가 429(`RiotRateLimitException`)를 받으면:
  - `X-Rate-Limit-Type` 이 `application` 이면 routing 전체(app 정지 키), 그 밖(method·service·없음)이면 그 method 만(method 정지 키) 멈춘다 (지금과 같다).
  - 정지 시간은 `Retry-After`, 없으면 `default-pause`(1초).
  - Java 가 `SET riot:rl:pause:{bucket} 1 PX {ms}` 한 번을 보낸다. Lua 는 쓰지 않는다.
- 나중 429 가 먼저 걸린 정지를 덮어쓴다. 더 짧은 Retry-After 가 오면 정지가 짧아진다. 더 새로운 정보라 받아들인다 **[판단]**.
- `riot.api.rate_limited{routing, type}` 지표는 그대로 센다.
- 정지 키는 Redis 에 있으므로 모든 인스턴스가 같이 멈춘다.

## 7. 없애는 것

| 대상 | 처리 |
|---|---|
| `riot-complete.lua`, `riot-pause.lua` | 지운다 |
| `RedisRiotRateLimiter#complete`, `learnLimits`, 한도 캐시·다시 읽기·HSET(`cachedLimits`, `lastWrittenLimits`, `LIMITS_KEY`, `knownLimits`, `learn`) | 지운다 |
| `RiotRateLimiter` 인터페이스 | `pause(routing, methodOrNull, duration)` 만 남긴다. 이름은 그대로 **[판단]** |
| `RiotCallRecorder` | 응답 뒤 일은 `riot.api.responses{status,host}` 세기와 429 정지만 남긴다. `complete` 호출, 헤더 읽기, 한도 근접 정지(`NEAR_LIMIT_PAUSE`, `riot.ratelimit.near_limit`)를 지운다 |
| `RateLimitWindow.nearLimit` | 지운다. `parse` 는 설정 읽기와 `tools/riot-mock` 헤더 계약 테스트가 쓰므로 남긴다 |
| Redis `riot:rl:limits` 해시 | 더 이상 읽지도 쓰지도 않는다. 남아 있어도 해가 없다. 운영 Redis 에서는 배포 뒤 손으로 지워도 된다 |

- 규칙 ZSET 키와 토큰 이름은 그대로라 배포 전후로 기록이 이어진다.
- 배포 순간 남아 있는 옛 정지 키는 값이 "끝나는 시각" 이지만 새 Lua 는 있는지만 보므로, 만료될 때까지 정지로 본다. 문제없다.

## 8. 지표 · 대시보드 · 문서

- 대시보드 `docker/monitoring/grafana/dashboards/renewal-load-test.json`:
  - "허가 거절 (기간)", "유저 우선순위 허가 거절 (기간)" 의 `result="denied"` 를 `result!="granted"` 로 바꾼다.
  - "한도 근접 멈춤 (기간)" 패널을 지우고, "Riot 429 · 한도 근접 / 분" 에서 근접 쿼리를 빼 "Riot 429 / 분" 으로 바꾼다.
  - 거절을 사유(`result`)별로 보는 시계열을 하나 둔다. 배치는 **[빌더]**.
- `README.md` "Rate Limiting & 배치 삽입": 4번(응답 뒤 기록)을 429 정지만 남게 고치고, 6번 지표 목록을 고치고, 한도는 설정에 적는다는 것과 4장 표를 짧게 넣는다.
- `CLAUDE.md` 2장 데이터 흐름이 이미 옛 인터셉터 체인을 적고 있다. 에이전트가 고치지 않고 끝 보고에서 사용자에게 알린다.
- 11-05·11-09 설계 문서는 고치지 않는다. 이 문서가 무엇을 대체하는지 위에 적었다.

## 9. 위험과 한계

- **허가 시각으로만 센다.** Riot 은 도착 시각으로 센다. 우리 기록은 "허가 → Riot 도착" 만큼 먼저 창을 빠지므로, 한도가 계속 꽉 찬 상태에서는 Riot 기준으로 한도를 넘길 수 있다. 차이는 보통 수십~수백 ms 이지만, 묶음(매치 20건)을 받고 차례로 보내는 동안 더 벌어진다. 상한을 90% 로 잡은 여유가 10초 이상 창에서는 이를 덮을 것으로 본다. 1초 창(개발 키 20:1 → 상한 18)은 여유가 2건이라 가끔 넘을 수 있다. 넘으면 429 정지가 받는다. 부하 테스트(10장)에서 429 가 나오면 다시 본다 (예: 기록 시각을 허가 시각 + 여유 ms 로 두기).
- **설정이 키와 다르면 모른다.** 실제보다 크게 적으면 429, 작게 적으면 덜 쓴다. 키를 바꾸거나 Riot 이 한도를 바꾸면 설정도 같이 바꿔야 한다.
- **같은 키를 쓰는 다른 곳은 보이지 않는다.** 로컬과 운영이 같은 키를 쓰면서 Redis 가 다르면, 서로의 호출을 세지 못한다. 지금은 한도 근접 정지(Count 헤더)가 이것을 미리 막는 유일한 장치였고, 이제는 429 가 나야 멈춘다.
- **routing 사이 원자성은 없다** (그대로). Riot ID 조회처럼 asia·kr 을 같이 받을 때 asia 만 허가되고 kr 이 거절되면, asia 토큰은 창이 끝날 때까지 자리를 차지한다.

## 10. 검증

- Java 를 바꾸므로 `./gradlew check` 를 돌린다 (repo 전체가 깨져 있으면 `:module:infra:riot-client:check` 와 바뀐 모듈별 check).
- 허가 Lua 는 Testcontainers Redis 로 확인한다 (지금 `RedisRiotRateLimiterPermitTest` 처럼). 고정해야 할 경우:
  1. app 창이 차면 `-1`, 어느 ZSET 에도 기록이 늘지 않는다.
  2. app 은 남고 method 창이 차면 `-2`, app ZSET 에도 기록이 늘지 않는다.
  3. app 과 method 가 모두 차면 `-1` (app 을 먼저 본다).
  4. app 정지 키나 묶음 method 의 정지 키가 있으면 `-3`. 다른 method 의 정지 키는 영향이 없다.
  5. BACKGROUND 가 초당 40 을 넘으면 `-4`. USER 는 백필 규칙을 보지 않는다.
  6. kr 의 app 창이 차도 asia 허가에는 영향이 없다.
  7. `method-limits` 에 없는 method 는 app 규칙만으로 허가된다.
  8. 허가되면 app·method·백필 ZSET 에 건수만큼 토큰이 기록된다.
  9. 유저 활동 중 DEFERRED·BACKGROUND 는 낮춘 상한에서 거절된다 (지금 테스트를 이어받는다).
- 설정: `method-limits` 이름 오타, 형식 오류, 빈 `app-limits` 면 기동(빈 생성)이 실패한다.
- `RiotCallRecorder`: 2xx·4xx·5xx 응답 뒤 limiter 를 부르지 않는다. 429 `application` 이면 app 정지, `method`·`service`·없음이면 그 method 정지, Retry-After 가 없으면 1초.
- 부하 테스트: 사용자가 앱을 띄운 뒤 `bash load-test/run.sh renewal-steady.js` (RATE=1, 5분)를 돌린다. 기준은 지난 실행(testid 20261110-225617, 3초 안 92.5%)이다. Riot 429 가 0 이고, 허가 거절이 사유별로 대시보드에 보이는지 본다.

## 11. 빌더에게 맡기는 것

- Lua 의 KEYS·ARGV 배치, Java 안 private 메서드와 레코드 이름.
- 테스트를 어느 파일에 둘지 (`RedisRiotRateLimiterLearnTest` 는 지운다).
- 대시보드 패널 배치.
- 4장의 아직 모르는 method 한도 채우기.

## 12. 순서 (계획에서 단계로 나눈다)

1. 설정(`appLimits`·`methodLimits`, 프로파일 yml)과 허가 Lua·`RedisRiotRateLimiter` 의 허가 쪽, 테스트.
2. 429 정지를 SET 으로 바꾸고 `RiotCallRecorder`·`RiotRateLimiter` 정리, Lua 두 개 삭제, 테스트.
3. 지표 result 값, 대시보드, README, `.env.example`.
4. `./gradlew check`, 부하 테스트.
