# Simplify Review: timeline-backfill spring-batch

**Date:** 2026-04-20
**Target:** Spring Batch 기반 타임라인 백필 기능 신규 도입 — `app/src/main/java/com/mmrtr/lol/backfill/` 신규 패키지(6개 파일), `core/enum/.../Platform.java` 수정, `app/build.gradle` 의존성 추가, `app/src/main/resources/application.yml` 배치 설정, 서브모듈 `lol-db-schema/db/migration/V20__spring_batch_metadata.sql` 신규.

---

## 1. Code Reuse Review

### Findings

| # | Item | Action |
|---|------|--------|
| 1 | `BackfillRateLimiterConfig`가 `ListenerRateLimiterConfig`와 3줄짜리 `setRate(...)` 패턴 중복. 공용 factory 제안 | **Skip** — 두 군데 3줄 중복. CLAUDE.md의 "세 번 유사하면 추상화, 두 번은 유지" 원칙에 부합. 배경 limiter가 추가되는 시점에 factory화 재검토 |
| 2 | `Platform.valueOfPlatformId`가 linear scan, 기존 `valueOfName`은 HashMap. 일관성·성능 차이 | **Fixed** — 대칭되는 `PLATFORM_ID_MAP` static 초기화로 전환. 두 lookup 모두 O(1) |
| 3 | `CompletableFuture.get(timeout)` 예외 처리 패턴이 `MatchDataProcessor`와 동일 | **Skip** — 공용 헬퍼를 만들 정도의 반복은 아님 |
| 4 | `JdbcPagingItemReader` 사용 스타일 | **Skip** — 프로젝트 첫 도입이라 비교 대상 없음 |
| 5 | `TimelineEventFrameWriter`의 `new ArrayList<>(chunk.getItems())` 복사 | **Skip** — `TimelineEventJsonService.saveAll(List<TimelineDto>)`는 invariant 타입 요구. `Chunk.getItems()`는 `List<? extends TimelineDto>` → 그대로 전달 시 타입 에러. 복사가 가장 명확 |
| 6 | `asyncJobLauncher` 별도 bean | **Skip** — Spring Boot 자동 구성된 JobLauncher는 동기(SyncTaskExecutor 기본). REST 컨트롤러에서 즉시 응답하려면 async 버전이 필수 |

---

## 2. Code Quality Review

### Findings

| # | Item | Severity | Action |
|---|------|----------|--------|
| 1 | `asyncJobLauncher` 신규 bean이 auto-configured `JobLauncher`와 모호성 유발 가능. 다른 곳에서 `JobLauncher`를 bare 주입하면 의도치 않은 선택 발생 | High | **Skip** — 컨트롤러는 `@Qualifier("asyncJobLauncher")`로 명시 주입. 다른 코드는 JobLauncher를 주입하지 않음 (현재 단일 소비자). 필요시 `@Primary`는 auto-config 쪽을 우선시하는 Spring Boot 동작에 맡김 |
| 2 | `InterruptedException` 캐치 후 interrupt 플래그 세팅 + `return null` 로 Spring Batch에 전파되지 않음 | High | **Skip** — 기존 `MatchDataProcessor`와 동일 패턴. interrupt 시 후속 DB·리포 호출에서 터져 step이 중단되는 구조. 현 단계에선 일관성 유지 |
| 3 | `SimpleAsyncTaskExecutor`가 각 POST마다 무제한 스레드 생성 — 관리자 API지만 잠재 DoS 벡터 | High | **Fixed** — `ThreadPoolTaskExecutor(core=1, max=2, queue=4, AbortPolicy)`로 전환 |
| 4 | `valueOfPlatformId` linear scan, `valueOfName`은 HashMap | Medium | **Fixed** — Reuse #2와 동일 수정 |
| 5 | `@Qualifier("timelineBackfillRateLimiter")` 등 매직 스트링 3곳 | Medium | **Skip** — 각 qualifier가 해당 bean 정의에 인접해 있어 맥락 명확. 3건 규모는 상수화 이득보다 간접화 비용이 큼 |
| 6 | Rate limiter config 중복 | Medium | **Skip** — Reuse #1과 동일 |
| 7 | `TimelineBackfillJobConfig` 5개 의존성 | Medium | **Skip** — Spring Batch config의 일반적 규모 |
| 8 | 튜닝 상수 미외부화 (`@ConfigurationProperties` 권장) | Low | **Skip** — 현재 값이 사용자 요구(40 req/s, 600 page)에 매칭. 필요해지면 externalize |
| 9 | Processor의 null 반환 silent failure | Low | **Skip** — `skipLimit` 카운터와 로그로 관찰 가능 |
| 10 | saveState(false) Javadoc 없음 | Low | **Skip** — CLAUDE.md 가이드상 불필요 주석 지양 |
| 11 | 패키지 Javadoc 없음 | Low | **Skip** — 위와 동일 |

---

## 3. Efficiency Review

### Findings

| # | Item | Impact | Action |
|---|------|--------|--------|
| 1 | WORKER_THREADS=10이 40 req/s 레이트리밋 병목에 비해 과다 | High | **Skip** — HTTP 호출 지연(수백 ms) 감안 시 병렬 호출이 필요. 10 스레드 × 250ms = 40 req/s 근접이 현실적. 1 스레드로 줄이면 처리량 급감 |
| 2 | `timeline_event_frame.match_id` 인덱스 누락 — NOT EXISTS 쿼리 성능 | High | **Skip** — V19의 복합 PK `(match_id, timestamp, event_index)`의 leftmost가 `match_id`라 자동 인덱스 역할. 별도 인덱스 불필요 |
| 3 | `ThreadPoolTaskExecutor`에 rejection handler 미지정 (기본 AbortPolicy) — 큐 가득 시 실패 | Medium | **Fixed** — `CallerRunsPolicy`로 전환. 큐 가득 시 reader thread가 직접 처리하여 back-pressure 자연 발생 |
| 4 | listener 20/s + backfill 40/s 결합 → region cap 46/s 초과 위험 | Medium | **Skip** — 현재 `@RabbitListener`가 주석 처리되어 있어 listener 미가동. 활성화 시점에 재조정 |
| 5 | per-call 30s 타임아웃 | Medium | **Skip** — 기존 `MatchDataProcessor`와 일관. 실측 후 조정 |
| 6 | Writer의 ArrayList 복사 | Negligible | **Skip** — Reuse #5와 동일 이유(타입 요구) |
| 7 | Batch 메타 테이블 쓰기 빈도 | Negligible | **Skip** — 초당 1회 수준, 문제 없음 |

---

## Summary

| File | Change |
|------|--------|
| `core/enum/.../Platform.java` | `PLATFORM_ID_MAP` static 초기화 + `valueOfPlatformId` O(1) HashMap lookup |
| `app/.../backfill/TimelineBackfillJobConfig.java` | `timelineBackfillStepTaskExecutor` rejection handler `CallerRunsPolicy` 추가. `asyncJobLauncher`의 내부 executor를 `SimpleAsyncTaskExecutor` → `ThreadPoolTaskExecutor(core=1, max=2, queue=4)` 로 교체 |

**Fix count:** 3 fixed / 21 skipped (대부분 skip은 의도적 설계 유지·premature abstraction 회피 사유)

**Build result:** BUILD SUCCESSFUL (`./gradlew build`, 57s)
