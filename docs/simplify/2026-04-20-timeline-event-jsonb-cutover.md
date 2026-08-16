# Simplify Review: timeline-event jsonb cutover

**Date:** 2026-04-20
**Target:** feature/timeline-event-jsonb 브랜치의 uncommitted 변경 — 9개 정규화 이벤트 테이블 쓰기 중단 및 `timeline_event_frame` JSONB로 통합. 추가로 신규 `TimelineEventJsonService` / `TimelineEventJsonRepositoryImpl` / `TimelineEventFrameEntity` 등 포함.

---

## 1. Code Reuse Review

### Findings

| # | Item | Action |
|---|------|--------|
| 1 | `TimeLineRepositoryImpl.findExistingMatchIds`와 `TimelineEventJsonRepositoryImpl.findExistingMatchIds`가 동일한 `SELECT DISTINCT match_id FROM <table> WHERE match_id IN (:ids)` 패턴. 공용 헬퍼 추출 제안 | **Skip** — 두 군데, 10줄 수준 중복. CLAUDE.md 가이드("세 번 비슷한 줄이 미성숙한 추상보다 낫다")에 따라 지금은 유지. 추가 테이블이 생기면 그때 공용화 |
| 2 | `MapSqlParameterSource[]` 배열 빌드 패턴 중복 | **Skip** — 엔티티 타입별 고유 매핑이라 일반화 이득이 적음 |
| 3 | `ObjectMapper.copy().setSerializationInclusion(...)`이 `TimelineEventJsonService` 생성자에서 인라인. 공용 `@Bean` 제안 | **Skip** — 해당 설정을 쓰는 곳이 한 군데뿐. 단일 사용처에 대한 프리메이처 추상화 회피 |
| 4 | 두 서비스가 각자 `existingMatchIds.contains → skip` 동일 패턴 반복 | **Skip** — 10줄 미만의 단순 루프 중복이며, 서로 다른 "이미 존재" 판정 기준(participant_frame vs timeline_event_frame)을 사용하므로 병합 시 의미가 섞임 |

---

## 2. Code Quality Review

### Findings

| # | Item | Severity | Action |
|---|------|----------|--------|
| 1 | `TimelineEventJsonService`의 `ObjectMapper` 직렬화 정책이 `JsonInclude.Include.NON_DEFAULT` → `goldGain=0`, `bounty=0`, `position={0,0}` 같은 유의미한 기본값이 JSON에서 누락. 이후 `data->>'goldGain'` 쿼리가 null 반환 | **High** | **Fixed** — `NON_NULL`로 변경해 null만 제외하고 0/false는 보존 |
| 2 | 두 서비스가 독립적으로 `findExistingMatchIds`를 호출해 서로 다른 테이블을 조회. 두 테이블 상태가 어긋나면 한쪽만 기록될 수 있음 | High | **Skip** — dual-write 과도기 설계의 의도적 분리. 롤백 여지 확보를 위해 각 저장소가 자신의 테이블 기준으로 판단하는 것이 오히려 안전 |
| 3 | `MatchService.saveAll`이 두 타임라인 서비스를 오케스트레이션 | Medium | **Skip** — 설계 의도대로 상위에서 병렬 호출 (아래 Efficiency #1 수정으로 병렬화됨) |
| 4 | `TimelineEventJsonRepositoryImpl.findExistingMatchIds`의 `@Transactional(readOnly=true)`가 이미 `@Transactional`인 `MatchService.saveAll` 안에서 호출되어 redundant | Medium | **Skip** — 기존 `TimeLineRepositoryImpl.findExistingMatchIds`, `MatchService.findExistingMatchIds` 모두 동일 패턴. 독립 호출 호환을 위한 방어적 선언이며 프로젝트 일관 컨벤션. 해가 없음 |
| 5 | `log.debug("... 이미 존재, 스킵")` 등 한국어 로그 메시지 | Medium | **Skip** — `TimeLineService`, `MatchService` 등 기존 로그도 한국어. 프로젝트 전반 컨벤션에 부합 |
| 6 | `TimelineEventFrameEntity.data`를 `String`+`@JdbcTypeCode(SqlTypes.JSON)`로 저장 (JsonNode/Map이 더 이상적) | Low | **Skip** — 현재는 쓰기만 담당. 쓰기 전에 이미 직렬화된 JSON을 그대로 bind. 읽기 로직이 추가될 때 재평가 |
| 7 | Event `type`이 String 기반 (enum 아님) | Low | **Skip** — 외부 Riot API 입력 필드, 프로젝트 전체가 String으로 유지 중 |

---

## 3. Efficiency Review

### Findings

| # | Item | Impact | Action |
|---|------|--------|--------|
| 1 | `MatchService.saveAll`이 `timeLineService.saveAll` → `timelineEventJsonService.saveAll`을 순차 호출. 두 작업은 서로 독립적인 테이블 쓰기 | High | **Fixed** — 기존 `timelineSaveExecutor`로 두 호출을 `CompletableFuture.runAsync` 병렬화, `allOf(...).join()`으로 대기. 상단의 participant/challenges/team/ban bulk save와 동일 패턴 |
| 2 | 두 리포지토리가 각자 `SELECT DISTINCT match_id ...` 라운드트립 → 배치당 두 번 | Medium | **Skip** — 각 타겟 테이블의 존재 여부를 독립 판정해야 하므로 통합 시 의미가 섞임. 배치당 2 쿼리 수준은 수용 가능 |
| 3 | 두 서비스가 `timelineDtos → frames → events/participantFrames`를 각각 한 번씩 순회 | Low | **Skip** — 일반 매치 기준 프레임 수 미미하며, 분리가 유지보수 이득 |
| 4 | Jackson per-event `writeValueAsString` 호출이 이벤트 수만큼 발생 | Medium | **Skip** — 현 예상 부하(배치당 수천 건)에서 수용 범위. 프로파일링 결과 병목일 때 재평가 |
| 5 | `jdbcTemplate.batchUpdate`를 전체 엔티티로 한 번에 호출 (청킹 없음) | Low | **Skip** — Postgres `reWriteBatchedInserts=true` 로 드라이버 레벨 배칭 처리. 현 규모에선 안전 |
| 6 | `TimelineEventJsonRepositoryImpl.findExistingMatchIds`의 `new HashSet<>(result)` | Negligible | **Skip** — 마이크로 최적화 |
| 7 | `NON_DEFAULT` 직렬화의 쿼리 시점 부담 | Negligible | **Superseded** — Quality #1에서 `NON_NULL`로 바꾸며 함께 해소 |

---

## Summary

| File | Change |
|------|--------|
| `infra/persistence/.../match/service/TimelineEventJsonService.java` | Jackson `NON_DEFAULT` → `NON_NULL` 로 변경, 0/false 값 보존 |
| `infra/persistence/.../match/service/MatchService.java` | 타임라인 두 저장을 `timelineSaveExecutor` 위에서 `CompletableFuture`로 병렬 실행 |

**Fix count:** 2 fixed / 13 skipped (13건 중 대부분은 의도적 유지 또는 마이크로 최적화)

**Build result:** BUILD SUCCESSFUL (`./gradlew :infra:persistence:build`, 테스트 포함 47s)
