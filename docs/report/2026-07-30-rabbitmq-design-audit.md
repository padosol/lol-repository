# RabbitMQ 설계 요소 점검 (2026-07-30)

> 대상: `module/infra/rabbitmq` 전체 + 연관 설정(`rabbitmq-*.yml`, `application.yml`, `docker/docker-compose.yml`)
> 목적: 메시지 큐 기반 시스템을 설계할 때 반드시 결정해야 하는 요소를 정리하고, 현재 코드가 각 요소를 어떻게 다루고 있는지 근거와 함께 판정한다.

---

## 0. 요약

| # | 설계 축 | 판정 | 한 줄 요약 |
|---|---|---|---|
| A | 토폴로지 설계 | ⚠️ | Exchange 타입 선택은 합리적이나, 큐 3개 중 **1개만 실제로 소비**되고 나머지는 방치 |
| B | 메시지 계약 | ⚠️ | JSON 변환은 있으나 스키마 버전 · `messageId` · `correlationId` 부재, `__TypeId__` FQCN 결합 |
| C | 전달 보장(발행) | ❌ | Publisher Confirms / Returns 미설정 → **발행 유실을 감지할 수단이 없음** |
| D | 전달 보장(소비) | ❌ | DLX 를 선언했지만 리스너가 예외를 삼켜 **DLQ 로 갈 경로가 없음**, 재시도 정책 부재 |
| E | 멱등성 | ✅ | Redis 3중 방어(pending / processing / 분산 락)로 중복 소비 대응 |
| F | 흐름 제어 | ✅ | `prefetch=1` + concurrency 20 + `BlockingQueue.offer` → nack requeue backpressure 설계 양호 |
| G | 복원력 · 운영 | ⚠️ | heartbeat · graceful shutdown 은 있으나 큐 TTL/최대 길이 없음, classic queue, 커넥션 미분리 |
| H | 관측성 | ⚠️ | Prometheus 노출은 되어 있으나 **큐 적재량 · DLQ 전용 지표/알림 없음** |
| I | 테스트 | ❌ | RabbitMQ 통합 테스트(Testcontainers) 전무, 존재하는 2건은 순수 단위 테스트 |

**가장 시급한 문제**: `renewal.match.find.queue` 는 **발행은 계속되는데 소비자가 없다**(§4-1). TTL·최대 길이·DLX 도 없어 브로커 디스크가 단조 증가한다.

---

## 1. 현재 토폴로지 (코드에서 역추출)

`RabbitMqBinding` (`module/infra/rabbitmq/.../config/RabbitMqBinding.java`) 기준.

| Exchange | 타입 | Routing Key | Queue | 발행자 | 소비자 |
|---|---|---|---|---|---|
| `mmrtr.exchange` | Topic | `mmrtr.key` | `mmrtr.summoner` | 메인 서버(주 경로) + `TierCollectionService:55` → `SummonerCollectionPublishAdapter` (admin 트리거 대량 발행) | ✅ `SummonerRenewalListener` |
| `summoner.dlx.exchange` | Direct | `deadLetter` | `mmrtr.summoner.dlx` | (DLX 자동) | ❌ 없음 |
| `mmrtr.matchId.exchange` | Direct | `mmrtr.routingkey.matchId` | `mmrtr.matchId` | ❌ 도달 불가 경로만 | ❌ 주석 처리 |
| `matchId.dlx.exchange` | Direct | `deadLetter` | `mmrtr.matchId.dlx` | (DLX 자동) | ❌ 없음 |
| `renewal.topic.exchange` | Topic | `renewal.match.find` | `renewal.match.find.queue` | ✅ `SummonerRenewalService:101` | ❌ **주석 처리** |

즉 **선언된 5개 큐 중 정상 동작하는 것은 `mmrtr.summoner` 하나뿐**이다.

```
[메인 서버] ──mmrtr.key──> mmrtr.summoner ──> SummonerRenewalListener  ✅ 동작
                                                      │
                                                      └─ hasMoreMatches 시
                                                         renewal.match.find ──> renewal.match.find.queue
                                                                                        │
                                                                                        ✗ 소비자 없음 → 무한 적재

mmrtr.matchId ──> MatchListener  ✗ 주석 처리 (발행자도 도달 불가)
```

---

## 2. 설계 시 결정해야 하는 요소 (체크리스트)

메시지 큐 도입 시 "코드를 쓰기 전에" 못을 박아야 하는 항목들. 각 항목의 현재 상태는 §3 에서 대조한다.

### A. 토폴로지
1. Exchange 타입 선택 — `direct`(정확 매칭) / `topic`(패턴) / `fanout`(브로드캐스트) / `headers`
2. 라우팅 키 설계 규칙 — 도메인·이벤트·버전을 어떤 순서로 담을지
3. 큐 · Exchange 네이밍 컨벤션 통일
4. **토폴로지 선언 소유권** — 애플리케이션이 선언(declarative)할지, IaC/운영이 선언할지
5. 큐 수명 속성 — `durable` / `exclusive` / `auto-delete`

### B. 메시지 계약
6. 페이로드 직렬화 형식 (JSON / Protobuf / Avro)
7. **스키마 버전 전략** — 필드 추가/삭제 시 발행자·소비자 배포 순서
8. 직렬화 타입 결합 제거 — FQCN 대신 논리적 타입 ID 매핑
9. 메시지 식별자 — `messageId`(멱등성 키), `correlationId`(추적), `timestamp`
10. 헤더 vs 페이로드 역할 분리

### C. 전달 보장 (발행 측)
11. **Publisher Confirms** — 브로커가 받았음을 확인
12. **Returns + `mandatory`** — 라우팅 실패(큐에 안 꽂힘) 감지
13. 메시지 영속성 — `deliveryMode=PERSISTENT`
14. **DB 트랜잭션과 발행의 원자성** — Transactional Outbox 필요 여부
15. 발행 실패 시 재시도 (`RetryTemplate`)

### D. 전달 보장 (소비 측)
16. ACK 모드 — `AUTO` / `MANUAL` / `NONE`
17. **재시도 정책** — 횟수, backoff, 재시도 위치(인메모리 vs 지연 큐)
18. **DLQ(Dead Letter Queue)** — poison message 격리
19. **DLQ 재처리 경로** — parking lot, 수동/자동 재투입 도구
20. 재시도 소진 시 동작 — `AmqpRejectAndDontRequeueException` vs requeue 루프 방지

### E. 멱등성 · 순서
21. at-least-once 전제 하의 **중복 소비 방어**
22. 순서 보장 필요 여부 (필요 시 단일 컨슈머 / 파티션 키)
23. 처리 중 중복 진입 방어 (분산 락)

### F. 흐름 제어 · 처리량
24. `prefetchCount` — 컨슈머당 미확인 메시지 상한
25. 컨슈머 동시성 (`concurrentConsumers` / `maxConcurrentConsumers`)
26. **Backpressure** — 하위 시스템 포화 시 큐에 되돌리는 수단
27. 외부 API rate limit 과의 결합
28. **발행/소비 커넥션 분리** — 소비 측 flow control 이 발행을 막지 않도록

### G. 복원력 · 운영
29. Heartbeat · 연결 타임아웃 · 자동 재연결
30. Graceful shutdown — in-flight 메시지 처리 보장
31. **큐 보호 장치** — `x-message-ttl`, `x-max-length`, `x-overflow`
32. 큐 타입 — classic vs **quorum**(복제·내구성) vs stream
33. **토폴로지 변경 배포 전략** — 큐 argument 변경 시 `PRECONDITION_FAILED` 회피
34. 자격 증명 · 권한(vhost, user permission)

### H. 관측성
35. 큐 적재량(depth) · consumer 수 지표 + **알림 임계치**
36. DLQ 유입 알림
37. 처리 지연(latency) · 실패율 메트릭
38. 분산 추적 — MDC/correlationId 전파
39. 발행/소비 로그의 상관관계

### I. 테스트
40. 브로커 통합 테스트 (Testcontainers)
41. 계약 테스트 — 발행자·소비자 스키마 호환
42. 실패 시나리오 테스트 — DLQ 유입, 재시도 소진, 브로커 다운

---

## 3. 항목별 적용 현황

### A. 토폴로지 — ⚠️

| 항목 | 상태 | 근거 |
|---|---|---|
| 1. Exchange 타입 | ✅ | 정확 매칭엔 `DirectExchange`(matchId, DLX), 확장 여지엔 `TopicExchange`(summoner, renewal) — 선택 근거가 타당 |
| 2. 라우팅 키 규칙 | ⚠️ | `mmrtr.key`, `mmrtr.routingkey.matchId`, `renewal.match.find`, `deadLetter` — 4가지 스타일이 혼재. 도메인/이벤트/버전 규칙 없음 |
| 3. 네이밍 컨벤션 | ⚠️ | `mmrtr.summoner` vs `renewal.match.find.queue` — 접두사·`.queue` 접미사 사용이 불일치 |
| 4. 선언 소유권 | ⚠️ | 애플리케이션이 `@Bean` 으로 선언(`RabbitMqConfig:41~133`). 편리하지만 §G-33 배포 리스크와 직결 |
| 5. 큐 수명 속성 | ✅ | 전부 `durable=true`, `exclusive`/`auto-delete` 미사용 — 컨슈머 서비스에 적절 |

`RabbitMqBinding` 을 enum 으로 중앙화해 exchange/routingKey/queue 를 한 곳에서 관리하는 점은 좋다. 다만 DLX routing key 가 두 곳 모두 `"deadLetter"` 문자열로 중복되어 있어(`RabbitMqBinding:10,12`) 향후 DLX 를 한 exchange 로 통합하면 충돌한다.

### B. 메시지 계약 — ⚠️

| 항목 | 상태 | 근거 |
|---|---|---|
| 6. 직렬화 | ✅ | `Jackson2JsonMessageConverter` (`RabbitMqConfig:155`) — 언어 중립적, 메인 서버와 연동에 적합 |
| 7. 스키마 버전 | ❌ | `SummonerMessage`, `SummonerRenewalMessage` 에 버전 필드 없음. 필드 추가 시 배포 순서 제약이 암묵적 |
| 8. 타입 결합 | ⚠️ | 기본 컨버터는 `__TypeId__` 헤더에 **FQCN** 을 기록한다. 메인 서버의 클래스 경로와 이 서비스의 `com.mmrtr.lol.infra.rabbitmq.dto.*` 가 어긋나면 역직렬화 실패 → `DefaultJackson2JavaTypeMapper` + `idClassMapping` 으로 논리 타입명 매핑이 필요 |
| 9. 메시지 식별자 | ❌ | `messageId`/`correlationId`/`timestamp` 미설정. 멱등성 키를 페이로드(`puuid`, `matchId`)에서 파생하고 있어 재시도 추적이 불가 |
| 10. 헤더 vs 페이로드 | ⚠️ | matchId 경로는 payload=matchId 문자열, `region` 만 헤더(`MessageSender:19`) — 페이로드에 함께 담는 편이 계약으로서 명확 |

`SummonerMessage` 는 `@Getter/@AllArgsConstructor/@NoArgsConstructor` 가변 클래스, `SummonerRenewalMessage` 는 `record` 로 스타일이 갈린다. 메시지 DTO 는 불변(`record`)이 원칙이므로 전자를 맞추는 것이 좋다.

### C. 전달 보장 (발행 측) — ❌

| 항목 | 상태 | 근거 |
|---|---|---|
| 11. Publisher Confirms | ❌ | `publisher-confirm-type` 설정 없음(`rabbitmq-*.yml` 3개 프로파일 모두), `setConfirmCallback` 없음(`RabbitMqConfig:148-152`) |
| 12. Returns / mandatory | ❌ | `publisher-returns`·`setMandatory`·`setReturnsCallback` 전무 → **라우팅 실패가 조용히 버려진다** |
| 13. 영속성 | ✅ | Spring AMQP 기본 `deliveryMode=PERSISTENT` + durable queue |
| 14. DB-발행 원자성 | ⚠️ | `SummonerRenewalService:92~106` 은 `save()` 후 별도로 `convertAndSend()`. 사이에서 죽으면 후속 매치 검색이 누락된다. Outbox 없음 |
| 15. 발행 재시도 | ❌ | `RabbitTemplate` 에 `RetryTemplate` 미설정 |

§A-3 의 큐 이름 오타나 바인딩 누락이 생겨도 §11·12 가 없으면 **아무 로그 없이 메시지가 사라진다.** 지금 구조에서 가장 값싸게 얻을 수 있는 안정성 개선점이다.

### D. 전달 보장 (소비 측) — ❌

| 항목 | 상태 | 근거 |
|---|---|---|
| 16. ACK 모드 | ⚠️ | 활성 리스너는 기본 `AUTO`. 비활성 `MatchListener` 는 `MANUAL` + `safeAck/safeNack` 구현 — 구현 품질은 좋으나 **동작하지 않는 코드** |
| 17. 재시도 정책 | ❌ | `spring.rabbitmq.listener.simple.retry.*` 설정 없음 → 재시도 비활성. backoff·최대 횟수 정의 없음 |
| 18. DLQ | ⚠️ | `mmrtr.summoner`·`mmrtr.matchId` 에 `x-dead-letter-exchange` 선언(`RabbitMqConfig:43,82`). 그러나 §20 때문에 **실제로 유입되지 않는다**. `renewal.match.find.queue` 는 DLX 자체가 없음(`RabbitMqConfig:124-126`) |
| 19. DLQ 재처리 | ❌ | DLQ 컨슈머·parking lot·재투입 도구 전무. 쌓이면 수동 개입 외 방법 없음 |
| 20. 재시도 소진 동작 | ❌ | `SummonerRenewalListener:46-48` 이 `catch (Exception e) { log.error(...) }` 로 **모든 예외를 삼킨다** → 항상 정상 ACK → DLX 선언이 사문화 |

이 축이 현재 설계의 가장 큰 구멍이다. **DLX 를 선언해 두었기 때문에 "실패 메시지는 DLQ 로 간다"고 착각하기 쉽지만, 예외를 삼키는 리스너 때문에 실패한 갱신 요청은 그대로 소실된다.**

추가로 `SummonerRenewalListener:39-42` 는 분산 락 획득 실패 시 `return` 하며 메시지를 ACK 한다. "이미 진행 중이니 버린다"는 의도는 이해되지만, 선행 갱신이 실패로 끝나면 두 요청 모두 유실된다.

### E. 멱등성 · 순서 — ✅

| 항목 | 상태 | 근거 |
|---|---|---|
| 21. 중복 소비 방어 | ✅ | `MatchRedisService.tryMarkPending`(SETNX, TTL 10분) / `tryMarkProcessing`(TTL 30분) 2단 + DB `findExistingMatchIds` 선조회 |
| 22. 순서 보장 | ✅ | 도메인상 순서 불필요(매치별 독립). `findQueueSimpleRabbitListenerContainerFactory` 는 concurrency=1 로 직렬화 — 의도적 선택 |
| 23. 처리 중 중복 진입 | ✅ | `RedisLockHandler.acquireLock(puuid, 3분)` (`SummonerRenewalListener:39`) |

at-least-once 전제를 정확히 이해하고 만든 부분. 다만 `PENDING_TTL=10분`은 "발행 후 소비까지"를 덮는 값이므로 §4-1 처럼 소비가 정체되면 같은 matchId 가 재발행될 수 있다 — TTL 을 큐 처리 SLA 보다 길게 잡거나, DB 유니크 제약을 최종 방어선으로 두어야 한다.

### F. 흐름 제어 · 처리량 — ✅

| 항목 | 상태 | 근거 |
|---|---|---|
| 24. prefetch | ✅ | 3개 팩토리 모두 `setPrefetchCount(1)` — 작업당 소요가 길고 편차가 큰 워크로드에서 공정 분배에 적합 |
| 25. 컨슈머 동시성 | ✅ | 갱신·배치는 20, 매치 검색은 1 (`RabbitMqConfig:188,208,229`) + Virtual Thread executor(`:161-173`) |
| 26. Backpressure | ✅ | `MatchBatchProcessor.add()` 가 `offer()` 로 비차단 → 포화 시 `QUEUE_FULL` → `basicNack(requeue=true)` (`MatchDataProcessor:69-72`, `MatchListener:42`). 설계 의도가 주석에도 남아있다(`MatchBatchProcessor:22`) |
| 27. 외부 rate limit 결합 | ✅ | Redisson `RRateLimiter` 20 req/s (`ListenerRateLimiterConfig:14`) + riot-client 인터셉터 체인 |
| 28. 커넥션 분리 | ❌ | `CachingConnectionFactory` 단일(`RabbitMqConfig:136-145`). `usePublisherConnection` 미사용 → 소비 측 flow control 이 발행을 동반 차단할 수 있다 |

`prefetch=1` + `concurrentConsumers=20` + 별도 인메모리 큐(500) + nack requeue 조합은 이 워크로드에 잘 맞는 설계다. 단 이 backpressure 경로가 §4-2 로 인해 **현재 실행되지 않는다**는 점이 아깝다.

### G. 복원력 · 운영 — ⚠️

| 항목 | 상태 | 근거 |
|---|---|---|
| 29. Heartbeat/타임아웃 | ✅ | `setRequestedHeartBeat(60)`(`RabbitMqConfig:142`), prod `connection-timeout: 30000ms` |
| 30. Graceful shutdown | ✅ | `server.shutdown: graceful` + `timeout-per-shutdown-phase: 30s` (`application.yml`) |
| 31. 큐 보호 장치 | ❌ | `x-message-ttl` / `x-max-length` / `x-overflow` **어느 큐에도 없음** → §4-1 이 디스크 고갈로 이어질 수 있다 |
| 32. 큐 타입 | ⚠️ | 전부 classic queue. 단일 노드라면 수용 가능하나, 내구성이 중요한 갱신 큐는 quorum queue 검토 대상 |
| 33. 토폴로지 변경 배포 | ❌ | 큐 argument 를 코드로 선언하므로, 기존 큐에 TTL·DLX 를 **추가하면 `PRECONDITION_FAILED`** 로 채널이 죽는다. 개선 시 큐 재생성 계획이 선행 필요 |
| 34. 자격 증명 | ⚠️ | prod 는 환경변수(✅). 로컬 `docker-compose.yml:21-22` 는 `guest/guest` + **볼륨 마운트 없음** → 컨테이너 재시작 시 durable 메시지 소실 |

§33 은 아래 개선안을 실행할 때 반드시 먼저 정해야 한다. `renewal.match.find.queue` 에 DLX/TTL 을 붙이려면 (a) 큐를 비우고 삭제 후 재선언, 또는 (b) `renewal.match.find.v2.queue` 로 신규 선언 후 전환하는 절차가 필요하다.

### H. 관측성 — ⚠️

| 항목 | 상태 | 근거 |
|---|---|---|
| 35. 큐 depth 지표·알림 | ❌ | Actuator `prometheus` 노출은 있으나(`application.yml:26`) 큐 적재량/consumer 수 알림 없음. §4-1 을 몇 달간 눈치채지 못할 수 있는 이유 |
| 36. DLQ 유입 알림 | ❌ | 없음 |
| 37. 처리 지연·실패율 | ⚠️ | 커스텀 Micrometer 지표는 riot-client 3종뿐(`riot.api.responses` 등). 큐 소비 latency·실패율 지표 없음. 갱신 소요는 `log.info` 수동 계측(`SummonerRenewalService:46,53,68`) |
| 38. 분산 추적 | ✅ | `@TraceLogging` + MDC (`module/support/logging`) 가 3개 리스너 전부에 적용 |
| 39. 발행/소비 상관관계 | ⚠️ | `correlationId` 가 없어 메인 서버 발행 ↔ 이 서비스 소비를 로그로 이어붙일 수 없다 |

### I. 테스트 — ❌

| 항목 | 상태 | 근거 |
|---|---|---|
| 40. 브로커 통합 테스트 | ❌ | `module/infra/rabbitmq/src/test` 에 `AsyncMatchSaverTest`, `MatchBatchProcessorTest` 2건뿐 — 둘 다 브로커를 띄우지 않는 순수 단위 테스트. Testcontainers 미사용 |
| 41. 계약 테스트 | ❌ | 메인 서버와의 메시지 스키마 호환 검증 없음 |
| 42. 실패 시나리오 | ❌ | DLQ 유입·재시도 소진·브로커 다운 테스트 없음 |

토폴로지 선언과 바인딩이 코드에 있으면서 이를 검증하는 테스트가 없다 — §4-1 같은 "발행자만 살아있는 큐"를 컴파일도 테스트도 잡아주지 못한 직접적 원인이다.

---

## 4. 발견된 결함 (심각도 순)

### 4-1. [치명] `renewal.match.find.queue` — 발행은 계속, 소비자는 없음

- 발행: `SummonerRenewalService.java:99-106` — 활성 코드. `hasMoreMatches` 는 `matchIds.size() == 20` (`MatchDataFetcher.java:46`) 이므로 **신규/오래된 소환사 갱신에서 거의 항상 true**
- 소비: `MatchFindListener.java:17-18` — `@RabbitListener` **주석 처리**
- 방어 장치: 큐에 `x-message-ttl` / `x-max-length` / DLX **전부 없음** (`RabbitMqConfig:124-126`)

결과: 갱신 트래픽에 비례해 브로커에 메시지가 **단조 증가**한다. 알림도 없어(§35) 디스크 압박 → 브로커 flow control → 단일 커넥션(§28) 때문에 정상 동작하는 summoner 갱신까지 동반 마비되는 경로가 열려 있다.

증가 속도는 `mmrtr.summoner` 소비량에 비례한다. 특히 `AdminTierCollectionController` → `TierCollectionService:55` 로 티어 수집을 돌리면 puuid 가 페이지 단위로 대량 발행되고, 각 갱신이 `hasMoreMatches` 를 만족할 때마다 `renewal.match.find.queue` 에 1건씩 적재된다 — **수집 규모가 곧 적재 규모**다.

`git log -S` 로 확인한 바 주석 처리 시점은 `e280008 refactor: 레이어 기반 모듈을 수직 바운디드 컨텍스트 모듈로 재구성 (#73)` 이다.

**대응(택 1)**
- (a) 소비자를 되살린다 — `MatchFindListener` 주석 해제. 단 이때 §4-2 의 `mmrtr.matchId` 경로도 함께 살려야 발행된 matchId 가 처리된다
- (b) 기능을 보류한다면 **발행부터 막는다** — `SummonerRenewalService:99-106` 제거 또는 feature flag 화. 큐/Exchange 선언도 함께 정리
- 어느 쪽이든 큐에 `x-max-length` + `x-overflow: reject-publish` 를 붙여 무한 적재를 구조적으로 차단 (단 §33 절차 필요)

### 4-2. [치명] 실패한 갱신이 조용히 사라진다 — DLX 무효화

```java
// SummonerRenewalListener.java:44-52
try {
    summonerRenewalService.renewSummoner(puuid, platform);
} catch (Exception e) {
    log.error(e.getMessage());   // ← 삼킨다 → 정상 ACK → DLQ 로 안 감
} finally { ... }
```

`mmrtr.summoner` 에 `x-dead-letter-exchange` 를 선언했지만(`RabbitMqConfig:43-44`) 예외가 컨테이너까지 전파되지 않으므로 **DLQ 는 영원히 비어 있다**. 재시도 정책도 없어(§17) Riot API 일시 장애 구간의 갱신 요청은 전량 손실된다.

또한 `log.error(e.getMessage())` 는 스택트레이스와 puuid 컨텍스트를 남기지 않아 사후 추적도 불가능하다.

**대응**
- 재시도 가치가 있는 예외(네트워크·5xx·타임아웃)는 **재던져서** 컨테이너 재시도 → 소진 시 DLQ 로 보낸다
- 재시도 무의미한 예외(잘못된 payload, 존재하지 않는 puuid)는 `AmqpRejectAndDontRequeueException` 으로 즉시 DLQ
- 로깅은 `log.error("갱신 실패 puuid={}", puuid, e)` 형태로 예외 객체를 함께 넘긴다
- `spring.rabbitmq.listener.simple.retry` (enabled/max-attempts/initial-interval/multiplier) + `default-requeue-rejected: false` 명시

### 4-3. [높음] Publisher Confirms / Returns 부재

발행이 브로커에 닿았는지, 큐에 라우팅됐는지 확인하지 않는다(`RabbitMqConfig:148-152`). 바인딩 오설정·브로커 재시작 구간의 유실이 **무징후**로 발생한다.

**대응**: `publisher-confirm-type: correlated` + `publisher-returns: true`, `setMandatory(true)`, `setConfirmCallback`/`setReturnsCallback` 에서 최소 `log.error` + 실패 카운터 메트릭.

### 4-4. [높음] `mmrtr.matchId` 파이프라인 전체가 도달 불가 코드

- `MatchListener` 소비 주석 처리(`MatchListener.java:24-28`)
- 발행 측 `MatchIdPublisher` 를 쓰는 것은 `SummonerCollectService` 와 `MatchFindService` 뿐이고, **이 둘을 호출하는 곳은 비활성 리스너뿐**이다(전체 검색 결과 외부 호출자 0건)
- 즉 `MatchDataProcessor`, `MatchBatchProcessor`, `MatchIdCollector`, `SummonerCollectService`, `MatchFindService`, `MessageSender`, `MatchIdPublisher` 가 모두 실행되지 않는다

이 안에는 §F 에서 높게 평가한 backpressure 설계와 `MANUAL` ack 구현이 들어있다 — **품질 좋은 코드가 실행되지 않는 상태**다. 큐·DLX·Exchange 선언(`RabbitMqConfig:80-116`)만 브로커에 남아 있어 토폴로지를 읽는 사람을 오해시킨다.

**대응**: 되살릴지 삭제할지 결정하고, 보류라면 최소한 `docs/` 와 코드 주석에 "의도적 비활성 + 재활성 조건"을 명시한다. 실행되지 않는 큐 선언은 제거한다.

### 4-5. [중간] `channelTransacted=true` 가 비용만 발생

`RabbitMqConfig:185` 는 `simpleRabbitListenerContainerFactory` 에만 채널 트랜잭션을 켠다. 채널 트랜잭션은 메시지 ACK 을 DB 트랜잭션과 묶을 때 의미가 있는데, 이 리스너는 예외를 삼켜(§4-2) 롤백이 발생할 수 없고 DB 트랜잭션 경계와도 연결되어 있지 않다. AMQP 트랜잭션은 라운드트립이 추가되어 처리량을 떨어뜨리므로, **§4-2 를 고치지 않는 한 순수 오버헤드**다.

**대응**: §4-2 수정과 함께 트랜잭션 경계를 실제로 설계하거나, 필요 없다면 `setChannelTransacted(false)`.

### 4-6. [중간] 발행/소비 커넥션 미분리

단일 `CachingConnectionFactory`(`RabbitMqConfig:136-145`)를 발행·소비가 공유한다. 브로커가 메모리 압박으로 flow control 을 걸면 소비 정체가 발행 차단으로 번진다 — §4-1 과 결합하면 장애가 전파된다.

**대응**: `RabbitTemplate.setUsePublisherConnection(true)`.

### 4-7. [중간] 메시지 계약의 취약점

- 스키마 버전 필드 없음 → 메인 서버와 이 서비스의 배포 순서 제약이 문서화되지 않은 채 암묵적
- `__TypeId__` FQCN 결합 → 발행 측 패키지 리팩토링이 소비 측을 깨뜨린다
- `messageId`/`correlationId` 없음 → 중복·지연 추적 불가

**대응**: `DefaultJackson2JavaTypeMapper` 에 논리 타입명 매핑, DTO 에 `eventId`·`version` 추가, 발행 시 `MessageProperties.messageId`/`correlationId` 세팅 후 소비 측 MDC 로 전파.

### 4-8. [낮음] 로컬 환경의 durable 무의미

`docker/docker-compose.yml:14-22` 에 볼륨 마운트가 없어 컨테이너 재생성 시 큐·메시지가 사라진다. 코드가 durable 을 전제로 하는데 로컬에서는 그 동작을 재현할 수 없다.

**대응**: `volumes: - rabbitmq-data:/var/lib/rabbitmq`.

### 4-9. [낮음] 네이밍·라우팅 키 컨벤션 혼재

`mmrtr.summoner` / `renewal.match.find.queue`, `mmrtr.key` / `renewal.match.find` / `deadLetter`. 큐가 늘어날수록 브로커 관리 UI 에서 소유 도메인 판별이 어려워진다.

**대응**: `{도메인}.{이벤트}.{v1}` + `.q` / `.dlq` 접미사 같은 규칙을 `RabbitMqBinding` 주석에 명시.

---

## 5. 잘 적용된 부분

과소평가하면 안 되는 부분들 — 대부분의 실무 코드보다 앞서 있다.

1. **멱등성 3중 방어** — DB 선조회 → Redis `pending`(SETNX) → Redis `processing` → 분산 락. at-least-once 를 정확히 전제한 설계
2. **Backpressure 설계** — `offer()` 비차단 enqueue → `QUEUE_FULL` → `basicNack(requeue=true)`. 인메모리 큐 용량 산정 근거를 주석으로 남긴 것(`MatchBatchProcessor:22`)까지 포함해 모범적
3. **`prefetch=1` 선택** — 작업 시간 편차가 큰 워크로드에서 컨슈머 간 공정 분배. 무심코 기본값(250)을 쓰지 않았다
4. **큐별 컨슈머 팩토리 분리** — 갱신 20 / 매치 검색 1 / 배치 20 을 워크로드 특성에 맞춰 분리
5. **Rate limit 이중화** — Redisson 전역 리미터 + riot-client 인터셉터 체인
6. **Virtual Thread 적용** — `Executors.newThreadPerTaskExecutor` + `@ConditionalOnProperty` 로 롤백 가능하게 플래그화
7. **헥사고날 준수** — 발행을 `SummonerCollectionPublishPort` 로 추상화해 도메인이 AMQP 를 모른다 (ArchUnit 강제)
8. **`RabbitMqBinding` enum 중앙화** — 문자열 산재 방지
9. **Graceful shutdown + heartbeat** — in-flight 메시지 보호와 좀비 커넥션 감지
10. **`safeAck`/`safeNack`** — 채널 폐쇄 상태를 확인하고 `AlreadyClosedException`/`IOException` 을 구분 처리 (다만 §4-4 로 미실행)

---

## 6. 개선 순서 제안

| 순위 | 작업 | 이유 | 난이도 |
|---|---|---|---|
| 1 | `renewal.match.find.queue` 발행/소비 정합성 결정 (§4-1) | 진행 중인 무한 적재 정지 | 낮음(발행 차단) / 중간(소비 활성) |
| 2 | 큐 depth · DLQ 유입 Prometheus 알림 (§35, §36) | 같은 유형의 사고 재발 감지 | 낮음 |
| 3 | 리스너 예외 처리 재설계 + 재시도/DLQ 정책 (§4-2) | 갱신 요청 유실 중단 | 중간 |
| 4 | Publisher Confirms / Returns (§4-3) | 발행 유실 가시화, 비용 대비 효과 최대 | 낮음 |
| 5 | `usePublisherConnection` 분리 (§4-6) | 장애 전파 차단 | 낮음 |
| 6 | `mmrtr.matchId` 파이프라인 존치/삭제 결정 (§4-4) | 도달 불가 코드 정리 | 중간 |
| 7 | 큐 보호 장치(`x-max-length`, TTL) + 토폴로지 마이그레이션 절차 (§31, §33) | 구조적 재발 방지 | 중간(큐 재생성 필요) |
| 8 | Testcontainers 기반 통합·실패 시나리오 테스트 (§40, §42) | 위 개선의 회귀 방지 | 중간 |
| 9 | 메시지 계약 정비 — 버전·`eventId`·타입 매핑 (§4-7) | 메인 서버와의 독립 배포성 확보 | 중간 |
| 10 | `channelTransacted` 재검토 (§4-5), 네이밍 컨벤션 (§4-9), docker 볼륨 (§4-8) | 정리성 개선 | 낮음 |

> 1·2번은 서로 독립적이며 둘 다 하루 내 처리 가능하다. 특히 2번(알림)을 먼저 넣으면 이후 개선의 효과를 측정할 수 있다.

---

## 7. See Also

- [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) — 모듈 의존 그래프 (`rabbit → league & match & summoner`, `rabbit → redis & riot-client`)
- [`docs/report/jdk21-virtual-thread-analysis.md`](./jdk21-virtual-thread-analysis.md) — 리스너 executor 의 VT 적용 배경
- `module/infra/rabbitmq/src/main/java/com/mmrtr/lol/infra/rabbitmq/config/RabbitMqConfig.java` — 토폴로지·컨테이너 팩토리 선언 지점
