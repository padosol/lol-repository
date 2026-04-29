# PostgreSQL 플래너 플립 — `match_participant_id` 범위 50 차이로 Nested Loop → Hash Semi Join 전환 사례

> **작성일**: 2026-04-22
> **출처 세션**: `7d6350bc-fcb6-4f74-aa70-670f1186eddd` (2026-04-21 timeline aggregation 검토)
> **관련 문서**: [`2026-04-21-timeline-jsonb-aggregation-review.md`](./2026-04-21-timeline-jsonb-aggregation-review.md)

## ⚠️ 전제 주의

이 사례의 대상 테이블 `timeline_event_frame` (JSONB 통합) 는 **현재 스키마에서 제거**되었고, 타임라인 이벤트는 `item_event`, `skill_level_up_event` 등 **이벤트별 테이블**로 전환되었습니다. 따라서 당시의 구체 쿼리는 재현 불가하지만, **플래너 플립의 메커니즘과 대응책은 현재 스키마에서도 그대로 유효**합니다.

---

## 1. 증상 (원문)

> "범위가 조금만 달라졌는데 cost 가 미친듯이 높아지는데요?
> `WHERE match_participant_id >= 1416523 AND match_participant_id < 1417200` 와
> `WHERE match_participant_id >= 1416523 AND match_participant_id < 1417150` 의 비용차이가 굉장히 많이납니다."
>
> "nested loop 에서 hash semi join 으로 변경되었네여"

- 범위 차이: **단 50 ids** (1417200 - 1417150)
- 플랜 변화: **Nested Loop (Semi Join)** → **Hash Semi Join**
- cost: 수백 ~ 수천 → **22.9M** 수준으로 점프

---

## 2. 당시 쿼리 (2억 행 JSONB 테이블 대상)

```sql
WITH
  mp_batch AS MATERIALIZED (
    SELECT match_participant_id, match_id, participant_id,
           puuid, champion_id, champion_name
    FROM match_participant
    WHERE match_participant_id >= :from AND match_participant_id < :to
  ),
  target_matches AS MATERIALIZED (
    SELECT DISTINCT match_id FROM mp_batch
  ),
  tef_events AS MATERIALIZED (
    SELECT tef.match_id, tef.timestamp AS frame_ts, tef.event_index,
           (tef.data->>'participantId')::int AS participant_id,
           tef.data->>'type'                 AS event_type,
           ...
    FROM timeline_event_frame tef
    WHERE tef.match_id = ANY(array(SELECT match_id FROM target_matches))
      AND tef.data->>'type' IN ('ITEM_PURCHASED', 'SKILL_LEVEL_UP')
  )
SELECT mp.puuid, ..., array_agg(...) ...
FROM mp_batch mp JOIN tef_events e ON ...
GROUP BY mp.puuid, mp.champion_id, mp.champion_name;
```

### EXPLAIN 비교 (요약)

**범위 627 rows (~1417150)** — Nested Loop 선택:
```
Nested Loop Semi Join
├─ Index Scan mp (rows=627)
└─ Index Scan tef PK (match_id = outer.match_id)
cost ≈ 수천 ~ 수만
```

**범위 677 rows (~1417200)** — Hash Semi Join 으로 플립:
```
Hash Semi Join                                   ← 플립 발생
  Hash Cond: (tef.match_id = target_matches.match_id)
  ->  Parallel Seq Scan on timeline_event_frame  ← 🚨 2억 행 probe
        Filter: (data->>'type'='ITEM_PURCHASED' AND ...)
  ->  Hash
        ->  CTE Scan on target_matches (50 rows 빌드)
cost ≈ 22,905,427
```

---

## 3. 왜 이런 일이 벌어지는가

### 핵심: **옵티마이저의 cost 모델은 계단함수**

플래너는 후보 플랜 N개의 cost 를 각각 계산하고 최저값을 고릅니다.

```
cost │
     │        ╱ 플랜 A (Nested Loop — outer_rows 에 비례)
     │       ╱
  k₂ ├──────╳────────── 플랜 B (Hash — 거의 상수)
     │     ╱│
     │    ╱ │
     └───┴──────────── outer_rows
      임계점 T
```

- outer_rows < T → 플랜 A (싸다)
- outer_rows > T → 플랜 B (A 가 더 비싸짐)
- **임계점 부근에서 50 rows 차이로도 플립 가능**
- 플립 순간 cost 가 불연속적으로 점프

### 이 케이스의 구체 원인

1. **`n_distinct(match_id)` 통계 부정확** → 매치당 rows 추정 과대 → Nested Loop 비용 과대 계산
2. **표현식 필터 `data->>'type'` 통계 부재** → 기본 selectivity (0.5%) 로 계산 → rows=183,845 로 추정됨 (실측과 괴리)
3. **`random_page_cost = 4.0` (SSD 부적합)** → Index Scan 비용 과대평가
4. **Parallel Seq Scan 의 워커 분할** → Seq Scan cost 가 나뉘어 상대적으로 싸 보임

---

## 4. Hash Semi Join 이 "뒤집힌" 방향이었던 이유

Semi Join 자체는 `IN (subquery)` / `EXISTS` 를 처리하는 정상 알고리즘입니다. 문제는 **어느 쪽이 probe, 어느 쪽이 build 였나**.

### Nested Loop Semi Join (좋은 플랜)
```
outer: target_matches (50 rows)       ← 작은 쪽이 driver
inner: timeline_event_frame PK scan   ← PK 으로 정확히 찌름
```
- 50 × (매치당 몇백 event PK scan) ≈ 수만 rows 접근
- 매치당 첫 match 찾으면 종료 (Semi Join 최적화)

### Hash Semi Join (나쁜 방향)
```
probe: Parallel Seq Scan timeline_event_frame (2억 rows!)
build: Hash on target_matches (50 rows)
```
- **큰 쪽을 probe 로 보냄** → 전체 순차 스캔 강요
- hash build 는 싸지만 probe 비용이 천문학적

**알고리즘(Hash Semi Join) 자체는 정상, 적용 방향이 뒤집혀서 큰 테이블을 probe 로 보낸 게 재앙**.

---

## 5. 진단 방법

### (1) 두 범위를 나란히 EXPLAIN

```sql
EXPLAIN (ANALYZE, BUFFERS) <쿼리 범위 A>;
EXPLAIN (ANALYZE, BUFFERS) <쿼리 범위 B>;
```

최상위 노드 비교: Nested Loop 가 사라지고 Hash/Merge 가 등장했다면 플립 확정.

### (2) 강제로 한쪽 플랜을 막고 실제 비용 측정

```sql
SET LOCAL enable_mergejoin = off;
SET LOCAL enable_hashjoin  = off;
EXPLAIN (ANALYZE, BUFFERS) <범위 B>;   -- Nested Loop 의 실측 비용 확인

RESET enable_mergejoin;
RESET enable_hashjoin;
```

종종 "실제로는 Nested Loop 이 훨씬 빠른데 플래너가 과대추정한 것" 으로 판명됨.

### (3) 위험 신호 패턴 (EXPLAIN 에서 즉시 버릴 것)

```
Hash Semi Join
  ->  Parallel Seq Scan on <거대 테이블>   ← 🚨 probe 가 큰 테이블
  ->  Hash
        ->  CTE Scan on <작은 집합>
```

→ probe 측이 Seq Scan 인 Hash Semi Join 이면 무조건 재검토.

---

## 6. 대응책

### 즉시 적용 가능

| 조치 | 효과 | 적용 위치 |
|---|---|---|
| `SET random_page_cost = 1.1` | SSD 환경에서 Index Scan 비용 적정화 → Nested Loop 가 이길 가능성 ↑ | 세션 / postgresql.conf |
| `ALTER TABLE ... ALTER COLUMN match_id SET STATISTICS 5000` + `ANALYZE` | match_id 통계 정밀도 ↑ → n_distinct 정확 추정 | DDL 1회 |
| `CREATE STATISTICS` 로 표현식 통계 추가 | `data->>'type'` selectivity 정확화 | DDL 1회 |
| chunk size 축소 (예: 500 participant ≈ 50 매치) | 임계점 아래 유지 → 플립 자체 회피 | 배치 코드 |

### 구조적 개선

**앱에서 2단계로 분리** (가장 확실):
```java
// 1단계: match_id 50개 뽑기
List<String> matchIds = jdbc.query("SELECT DISTINCT match_id FROM match_participant WHERE ...");

// 2단계: ANY(array) 바인딩으로 이벤트 조회
jdbc.query("SELECT ... FROM item_event WHERE match_id = ANY(?)",
           ps -> ps.setArray(1, conn.createArrayOf("text", matchIds.toArray())));
```

- 플래너에게 "subquery semi-join" 이 아니라 **확정된 배열 상수**를 주므로 플립 불가
- **`IN (SELECT ...)` 은 semi-join 3종 (Nested/Hash/Merge) 중 선택 경쟁** → 플립 위험
- **`= ANY(?)` 바인딩은 Semi Join 선택 과정 자체가 없음** → 플랜 구조 단조롭고 예측 가능

### JDBC 특수 함정: `prepareThreshold`

PostgreSQL JDBC 는 같은 PreparedStatement 를 **5회 이상 실행**하면 custom plan → **generic plan** 으로 전환:

- Custom plan: 실제 파라미터 값으로 rows 추정
- Generic plan: 평균 selectivity 가정 → **5번째 실행부터 갑자기 느려짐 (= 플립)**

해결:
```
jdbc:postgresql://.../db?prepareThreshold=0
```
또는 세션 레벨로:
```sql
SET plan_cache_mode = force_custom_plan;
```

---

## 7. 교훈 (현재 스키마 `item_event` / `skill_level_up_event` 등에도 적용)

1. **outer rows 경계에서의 플립은 50 rows 차이로도 발생 가능** → 배치 사이즈는 임계점보다 여유 있게 **아래쪽**으로 잡아야 함
2. **`IN (SELECT ...)` 보다 앱 2단계 + `= ANY(?)` 바인딩**이 플랜 안정성 면에서 유리 (특히 대량 배치)
3. **표현식 인덱스/필터가 있다면 `CREATE STATISTICS` 로 통계 명시** — 그렇지 않으면 플래너가 기본 selectivity 로 오판
4. **Hash Semi Join 의 probe 가 거대 테이블 Seq Scan** 이면 무조건 재작성 대상
5. **JDBC `prepareThreshold=0`** 은 대량 배치·동적 파라미터 쿼리의 flip 방어선
6. **`EXPLAIN (ANALYZE, BUFFERS, SETTINGS)` 를 기본 세트**로 — cost 추정이 아니라 실측과 설정 영향까지 봐야 플립 원인 추적 가능

---

## 8. 참조

- [원본 리뷰 문서 — JSONB 집계 설계 검토](./2026-04-21-timeline-jsonb-aggregation-review.md)
- PostgreSQL 공식: [Using EXPLAIN](https://www.postgresql.org/docs/current/using-explain.html)
- PostgreSQL 공식: [Extended Statistics](https://www.postgresql.org/docs/current/planner-stats.html#PLANNER-STATS-EXTENDED)
- pgjdbc: [Server Prepared Statements](https://jdbc.postgresql.org/documentation/server-prepare/)
