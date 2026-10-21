# module:domain:championstats

챔피언 통계(BigQuery 집계) 조회. 공개 API `/api/v1/champion-stats/**` (공개 API 재설계 3.4절, MP-132). sink 컨텍스트 — 다른 도메인 모듈에 의존하지 않는다.

## 책임 / 구조

`com.mmrtr.lol.domain.championstats` 하위:
- `domain/` — `TierFilter` (단일 티어 `EMERALD` 또는 범위 `MASTER+`)
- `application/port/` — `ChampionStatsQueryPort`(BigQuery)·`ChampionStatsCachePort`(캐시 + single-flight 락)·`ChampionStatsMetricsPort`
- `application/usecase/` — `ChampionStatsQueryUseCase`
- `readmodel/` — 공개 API 응답 모델. **lol-server 응답과 필드가 같아야 한다** (`ChampionStatsControllerTest` 가 검증)

## 공개 API — 챔피언 통계

lol-server championstats 모듈을 옮긴다. 쿼리·tier 규칙·Wilson 하한은 lol-server 와 같다.

- `GET /api/v1/champion-stats?platform=&patch=&tier=` — 포지션별 챔피언 승률·픽률·밴률·tier (BigQuery 가 정한 순서·tier)
- `GET /api/v1/champion-stats/{championId}?platform=&patch=&tier=` — 포지션별 상세. 포지션마다 매치업·룬·주문·스킬·시작 아이템·신발·3코어를 동시에 읽고(`requestExecutor`), 빌드류에는 Wilson 95% 하한을 붙인다. 픽률·밴률·tier 는 목록 통계(캐시 공유)에서 가져온다
- 세 파라미터 모두 필수. 모르는 platform·patch 형식(`14.24`)은 400 `INVALID_INPUT`, 잘못된 tier 는 400 `INVALID_TIER_FILTER` (lol-server 는 잘못된 patch 에 500)
- 캐시: 6시간, 미스는 single-flight — 락(대기 3초·lease 30초)을 잡은 요청만 BigQuery 를 부르고, 못 잡으면 200ms × 15 캐시를 기다린 뒤 직접 부른다(`lol.bq.single_flight.fallback` 카운터). lol-server 의 `champion-stats.cache.enabled` 토글은 옮기지 않았다
- 옛 경로 `/api/v1/{platformId}/champion-stats/**` 는 옮기지 않는다

## 어댑터 위치

이 컨텍스트의 port 를 구현/소비하는 어댑터는 `module:infra:*` 에 있다: bigquery(`ChampionStatsBigQueryAdapter`·`ChampionStatsMetricsAdapter`)·redis(`ChampionStatsCacheRedisAdapter`)·api(`controller/championstats/ChampionStatsController`).

## 규칙

- 도메인은 인프라 타입(BigQuery·RedisTemplate·Redisson·Micrometer)을 import 하지 않는다.
- 검증: `./gradlew :module:domain:championstats:test` (ArchUnit) / `./gradlew :module:domain:championstats:compileJava`.
