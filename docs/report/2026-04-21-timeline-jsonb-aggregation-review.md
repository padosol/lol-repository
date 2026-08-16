# timeline_event_frame JSONB 집계·조회 설계 리뷰

**Date:** 2026-04-21
**Scope:** V19 `timeline_event_frame` (JSONB + GIN) 테이블을 기반으로 한 통계 집계 쿼리 설계, 2억 건 규모에서의 성능 이슈, 배치 처리 패턴, PostgreSQL 옵티마이저 관련 원칙 정리.

---

## 1. 전제: 테이블 구조

```sql
CREATE TABLE timeline_event_frame (
    match_id    VARCHAR(255) NOT NULL,
    timestamp   BIGINT       NOT NULL,    -- frame.timestamp
    event_index INTEGER      NOT NULL,    -- frame.events[] 배열 인덱스
    data        JSONB        NOT NULL,    -- EventsTimeLineDto 전체
    PRIMARY KEY (match_id, timestamp, event_index)
);

CREATE INDEX idx_timeline_event_frame_data_gin
    ON timeline_event_frame USING GIN (data jsonb_path_ops)
    WITH (fastupdate = on, gin_pending_list_limit = 16384);
```

- `data` JSONB 내부 주요 필드: `type`, `participantId`, `itemId`, `beforeId`, `afterId`, `skillSlot`, `levelUpType`, `timestamp`
- GIN `jsonb_path_ops` 는 `@>` (containment) 만 지원
- **현재 적재량: 2억 행 / 26만 매치 (매치당 평균 ~770 events)**

---

## 2. 집계 쿼리 설계 (아이템 빌드 기준)

### 2.1 시작 아이템 집계 (ITEM_UNDO 반영, 1분 이내, 장신구 제외)

기존 `v_starting_items` 뷰의 JSONB 버전:

```sql
WITH item_net AS (
    SELECT
        tef.match_id,
        (tef.data->>'participantId')::int AS participant_id,
        CASE
            WHEN tef.data->>'type' = 'ITEM_PURCHASED' THEN (tef.data->>'itemId')::int
            WHEN tef.data->>'type' = 'ITEM_UNDO'      THEN (tef.data->>'beforeId')::int
        END AS effective_item_id,
        SUM(CASE tef.data->>'type'
                WHEN 'ITEM_PURCHASED' THEN 1
                WHEN 'ITEM_UNDO'      THEN -1
            END) AS net_count
    FROM timeline_event_frame tef
    WHERE (tef.data @> '{"type":"ITEM_PURCHASED"}'::jsonb
        OR tef.data @> '{"type":"ITEM_UNDO"}'::jsonb)
      AND tef.timestamp <= 60000
    GROUP BY tef.match_id, (tef.data->>'participantId')::int,
             CASE WHEN tef.data->>'type' = 'ITEM_PURCHASED' THEN (tef.data->>'itemId')::int
                  WHEN tef.data->>'type' = 'ITEM_UNDO'      THEN (tef.data->>'beforeId')::int
             END
)
SELECT mp.champion_id, mp.team_position,
       i.effective_item_id AS item_id,
       COUNT(*) AS pick_count,
       AVG(CASE WHEN mp.win THEN 1.0 ELSE 0.0 END) AS win_rate
FROM item_net i
JOIN match_participant mp
  ON mp.match_id = i.match_id AND mp.participant_id = i.participant_id
CROSS JOIN LATERAL generate_series(1, i.net_count) gs(n)
WHERE i.net_count > 0
  AND i.effective_item_id NOT IN (3340, 3363, 3364)
GROUP BY mp.champion_id, mp.team_position, i.effective_item_id
ORDER BY mp.champion_id, pick_count DESC;
```

### 2.2 최종 아이템 빌드 (ITEM_SOLD/DESTROYED 까지 반영)

`net_count` 로직에 `ITEM_SOLD`, `ITEM_DESTROYED` 를 -1 로 포함. 최종 보유 아이템만 집계.

### 2.3 빌드 순서 (첫 3 코어템)

`ROW_NUMBER() OVER (PARTITION BY ... ORDER BY timestamp)` + 소모품/장신구 제외 후 `STRING_AGG` 으로 빌드 경로 추출.

---

## 3. 2억 건 규모에서 발생하는 구조적 문제

| 문제 | 설명 |
|---|---|
| **JSONB detoast 비용** | `@>` GIN bitmap scan 결과의 heap row 마다 JSONB parse + TOAST read. ITEM_PURCHASED 6천만 rows 처리 시 수십 GB I/O |
| **GIN `fastupdate=on` pending list 누적** | 대량 INSERT 후 flush 시 autovacuum I/O 폭주 → SELECT 지연 |
| **ALTER TABLE ADD GENERATED STORED 금지** | 2억 건 full table rewrite, 수 시간 ACCESS EXCLUSIVE 락 |
| **CREATE INDEX CONCURRENTLY 도 수 시간** | WAL 폭증 + autovacuum 중단 영향 |
| **JSONB `@>` 기본 selectivity 0.1% 하드코딩** | 실제 30% 인 조건도 0.1% 로 추정 → 옵티마이저 오판 |

---

## 4. 개선 전략 (우선순위 순)

### 전략 1. 집계 전용 슬림 테이블 `item_event_flat` (가장 권장)

```sql
CREATE TABLE item_event_flat (
    match_id       VARCHAR(255) NOT NULL,
    participant_id SMALLINT     NOT NULL,
    event_type     SMALLINT     NOT NULL,  -- 1=PURCHASED, 2=UNDO, 3=SOLD, 4=DESTROYED
    item_id        INTEGER      NOT NULL,
    before_id      INTEGER,
    event_ts_ms    INTEGER      NOT NULL,
    PRIMARY KEY (match_id, participant_id, event_ts_ms, event_type, item_id)
) PARTITION BY HASH (match_id);

CREATE INDEX ON item_event_flat (event_type, item_id) INCLUDE (match_id, participant_id);
```

- row 당 ~40 bytes (JSONB 대비 1/10~1/20). 전체 ~2~3GB → 메모리 내 집계 가능
- `TimelineEventJsonService.saveAll` 내부에서 ITEM_* 4종만 추가 INSERT
- 백필은 기존 `TimelineBackfillJob` 동일 패턴으로 1회 실행

### 전략 2. ClickHouse 이관 (V12 clickhouse_etl_views 활용)

- PostgreSQL = 원장, ClickHouse = OLAP
- 2억 건 GROUP BY → 수 초 수준

### 전략 3. 파티셔닝 선행

- `HASH (match_id) MODULUS 32` 등으로 분할 → VACUUM/REINDEX 단위 축소

### 전략 4. 통계 개선

```sql
CREATE STATISTICS stat_tef_data_type ON (data->>'type') FROM timeline_event_frame;
ALTER TABLE timeline_event_frame ALTER COLUMN match_id SET STATISTICS 5000;
ANALYZE timeline_event_frame;
```

---

## 5. 단일 매치 조회 (사용자 화면용)

### 원칙: **PK 만으로 충분. `@>` 보다 `->>` 권장**

```sql
SELECT data
FROM timeline_event_frame
WHERE match_id = :matchId
  AND data->>'type' IN ('ITEM_PURCHASED', 'SKILL_LEVEL_UP')
ORDER BY timestamp, event_index;
```

### 이유
- `match_id = ?` 만으로도 PK leftmost 스캔 → ~500~2,000 rows
- `@>` 는 오히려 GIN 유도 → 잘못된 플랜 가능
- `->>` 는 GIN 후보에서 제외 → 플래너가 PK 경로 강제 선택
- 1 매치 2,000 rows 의 JSONB `->>` 파싱 비용은 무시 가능 (<5ms)

### type 인덱스 추가 여부 판단

**단일 매치 조회에는 추가하지 말 것.**

1. match_id 동등조건 selectivity 가 압도적 (1/26만)이라 type 인덱스 무의미
2. BitmapAnd 과정 자체가 오히려 오버헤드
3. 2억 건에서 인덱스 빌드 비용 과도
4. STORED generated column 은 full table rewrite 위험

인덱스 검토는 **글로벌 type 기반 조회** 시나리오에서만 필요하고, 그 경우는 슬림 테이블이 정답.

---

## 6. 배치 집계 시 발생한 성능 이슈와 원인

### 상황
- 26만 매치 × 10명 = 260만 match_participant row 대상 집계
- watermark 를 `match_participant_id` 로 설정, 500건/3,500건 chunk
- 매우 느림

### 원인 1. watermark 단위 ≠ 조인 단위

```
500 participant × 타임라인 조회 = 500 쿼리
  → 같은 매치 타임라인을 10번씩 중복 조회
```

- 동일 매치의 타임라인을 10번씩 읽음 → **I/O 10배**
- 매치 1회 읽기로 10명분 처리 가능 → watermark 를 `match_id` 로 전환

### 원인 2. `@>` + `jsonb_build_object` 로 옵티마이저 오판

```sql
-- 문제 쿼리 (느림)
JOIN timeline_event_frame tef
  ON tef.match_id = mp.match_id
 AND tef.data @> jsonb_build_object('participantId', mp.participant_id)
WHERE tef.data @> '{"type":"ITEM_PURCHASED"}'
```

- `@>` 두 개가 GIN 유도 → BitmapAnd 경로 선택
- GIN bitmap 빌드 비용 = **실제 매칭 TID 수 비례** (최종 교집합 크기가 아님)
- `jsonb_build_object(컬럼 참조)` 는 plan-time constant 가 아니라 selectivity 통계 없음 → 기본값 0.1% 오용

### 원인 3. 표현식 필터 통계 부재 → Seq Scan 선택

실제 EXPLAIN 결과:

```
Parallel Seq Scan on timeline_event_frame (cost=0..22,791,865)  ← 2억 행 full scan
  Filter: (data->>'type'='ITEM_PURCHASED' AND (data->>'timestamp')::int <= 120000)
  rows=183,845 (추정)
```

- `(data->>'type')` 같은 표현식에 통계 없음 → 기본 selectivity 로 추정
- `n_distinct(match_id)` 과소추정 가능 → Nested Loop 비용 과대 계산
- 결과: "2억 행 Seq Scan 이 차라리 싸다" 판단

---

## 7. 최종 권장 쿼리 (WITH MATERIALIZED + 범위 축소)

```sql
WITH
mp_batch AS MATERIALIZED (
    SELECT match_participant_id, match_id, participant_id,
           puuid, champion_id, champion_name
    FROM match_participant
    WHERE match_participant_id >= :from
      AND match_participant_id <  :to         -- 500건 단위 권장
),
target_matches AS MATERIALIZED (
    SELECT DISTINCT match_id FROM mp_batch
),
tef_events AS MATERIALIZED (
    SELECT
        tef.match_id,
        tef.timestamp                       AS frame_ts,
        tef.event_index,
        (tef.data->>'participantId')::int   AS participant_id,
        tef.data->>'type'                   AS event_type,
        (tef.data->>'itemId')::int          AS item_id,
        (tef.data->>'skillSlot')::int       AS skill_slot,
        tef.data->>'levelUpType'            AS level_up_type,
        (tef.data->>'timestamp')::bigint    AS event_ts_ms
    FROM timeline_event_frame tef
    WHERE tef.match_id IN (SELECT match_id FROM target_matches)
      AND tef.data->>'type' IN ('ITEM_PURCHASED', 'SKILL_LEVEL_UP')
)
SELECT
    mp.puuid,
    mp.champion_id,
    mp.champion_name,
    array_agg(e.item_id ORDER BY e.frame_ts, e.event_index)
        FILTER (WHERE e.event_type = 'ITEM_PURCHASED')   AS item_ids,
    array_agg(e.event_ts_ms ORDER BY e.frame_ts, e.event_index)
        FILTER (WHERE e.event_type = 'ITEM_PURCHASED')   AS item_timestamps,
    array_agg(e.skill_slot ORDER BY e.frame_ts, e.event_index)
        FILTER (WHERE e.event_type = 'SKILL_LEVEL_UP')   AS skill_slots,
    array_agg(e.event_ts_ms ORDER BY e.frame_ts, e.event_index)
        FILTER (WHERE e.event_type = 'SKILL_LEVEL_UP')   AS skill_timestamps,
    array_agg(e.level_up_type ORDER BY e.frame_ts, e.event_index)
        FILTER (WHERE e.event_type = 'SKILL_LEVEL_UP')   AS skill_level_up_types
FROM mp_batch mp
JOIN tef_events e
  ON e.match_id       = mp.match_id
 AND e.participant_id = mp.participant_id
GROUP BY mp.puuid, mp.champion_id, mp.champion_name;
```

### 설계 포인트

| 요소 | 역할 |
|---|---|
| `MATERIALIZED` | 플래너 인라인 방지, 실행 순서 고정 |
| `target_matches DISTINCT` | 10명 중복 매치 제거 → IN 절 축소 |
| `match_id IN (...)` | PK leftmost 강제, Seq Scan 회피 |
| `->>` | GIN 후보 배제, B-tree/PK 경로 유도 |
| `FILTER (WHERE ...)` | 단일 스캔으로 아이템/스킬 분리 집계 (카티션 회피) |
| `array_agg(... ORDER BY ...)` | 빌드 순서 보장 |
| 범위 500건 | 2억 Seq Scan → 50 매치 × PK scan (~17,000배 감소) |

---

## 8. 핵심 원리 정리

### 8.1 ON 절 vs WHERE 절 실행 순서

- **INNER JOIN**: ON 과 WHERE 의 predicate 는 옵티마이저에게 구분 없이 한 덩어리로 취급. 자유롭게 재배치됨
- **OUTER JOIN (LEFT/RIGHT/FULL)**: 의미론이 다름. ON 은 NULL 확장 전, WHERE 는 NULL 확장 후. 위치 바꾸면 결과 달라짐
- **성능 튜닝의 지렛대는 위치가 아니라 조건의 형태** (`@>` vs `->>`, 상수 vs 컬럼 참조)

### 8.2 CTE 실행 순서

- **의존성 DAG 순서**는 반드시 지켜짐
- **독립 CTE 는 순서 불확정** (플래너 재량)
- **PG 12+ 기본은 inline (NOT MATERIALIZED)** → 순서 개념 소멸
- **순서 강제하려면 `MATERIALIZED` 명시 필수**
- 2회 이상 참조 시 자동 materialized 지만, 의존해서는 안 됨

### 8.3 FILTER 절 원리

- `WHERE` = row-level 필터 (집계 입력 자체를 줄임)
- `FILTER` = **집계 함수별 개별 판단** (같은 row 가 집계 A 엔 포함, B 엔 제외 가능)
- 비용: O(N × M) 조건 평가 (N=행 수, M=FILTER 붙은 집계 수)
- I/O 비용 대비 무시 가능, **JOIN 카티션 (N^M) 대비 압도적으로 저렴**
- SQL:2003 표준, `CASE WHEN ... THEN x END` 관용구를 대체

#### 곱연산 vs 합연산
```
JOIN 방식:   cardinality = |items| × |skills| × |participants|  (곱)
FILTER 방식: cardinality = (|items| + |skills|) × |participants| (합)
```
이벤트 종류가 늘수록 차이가 기하급수적으로 벌어짐.

### 8.4 JSONB `@>` 의 함정

- 기본 selectivity 0.001 (0.1%) 로 하드코딩 → 실제 30% 인 조건도 과소추정
- BitmapAnd 의 GIN 입력 비용은 **교집합 크기가 아니라 posting list 전체 크기**에 비례
- **탐색용**엔 좋지만 **핫 path 집계용**엔 부적합
- `->>` 로 교체하면 GIN 후보에서 제외되어 플래너 혼란 제거

### 8.5 옵티마이저와 "먼저 필터"의 오해

- "match_participant_id 먼저 필터했으니 빠르겠지" 는 **작성자의 의도**
- 옵티마이저에겐 모든 조건이 동등한 predicate 집합의 원소
- 선택은 **비용 추정 게임** → 통계 부정확 시 나쁜 플랜 선택
- 플래너 설득보다 **쿼리를 쪼개서 선택지 제거**가 더 안정적

---

## 9. 배치 watermark 전략

| 원칙 | 이유 |
|---|---|
| **watermark 단위 = 가장 큰 조인 대상 단위** | 중복 조회 회피 |
| **match_participant_id 대신 match_id 권장** | 동일 매치 타임라인 중복 조회 방지 (10배 I/O 절감) |
| **chunk size 500 participant ≈ 50 매치** | 2억 Seq Scan 회피 가능한 규모 |
| **JDBC fetchSize = chunk size** | 왕복 횟수 일치 |
| **2단계 쿼리 분리도 유효** | 플래너가 엉뚱한 선택 못 하게 강제 |

---

## 10. 액션 아이템

### 즉시 적용
- [ ] 집계 쿼리를 WITH MATERIALIZED + `->>` + FILTER 패턴으로 전환
- [ ] chunk size 를 500 participant (≈50 매치) 단위로 축소
- [ ] `EXPLAIN (ANALYZE, BUFFERS)` 로 Seq Scan 소멸 확인
- [ ] SSD 환경이라면 `random_page_cost = 1.1` 세션 적용 검토

### 통계 개선 (야간 작업)
- [ ] `CREATE STATISTICS` 로 표현식 통계 추가
- [ ] `match_id` STATISTICS 상향 (5000)
- [ ] `ANALYZE timeline_event_frame` 재실행

### 중장기 설계
- [ ] 집계 전용 슬림 테이블 `item_event_flat`, `skill_event_flat` 설계 검토
- [ ] ClickHouse ETL 경로 활용 (V12 인프라)
- [ ] 파티셔닝 필요 시점 모니터링 (row 수 vs VACUUM 소요시간)

---

## 11. 참고

- V19 마이그레이션: `lol-db-schema/db/migration/V19__introduce_timeline_event.sql`
- 백필 잡: `app/src/main/java/com/mmrtr/lol/backfill/`
- 서비스 레이어: `infra/persistence/.../TimelineEventJsonService.java`
- 선행 리뷰: `docs/simplify/2026-04-20-timeline-event-jsonb-cutover.md`
- 선행 리뷰: `docs/simplify/2026-04-20-timeline-backfill-spring-batch.md`
