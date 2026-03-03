# ClickHouse 분석 DB 운영 가이드

## 아키텍처 개요

```
RIOT API --> Spring Boot --> PostgreSQL (OLTP)
                                |
                          외부 테이블 참조
                                |
                            ClickHouse (OLAP)
                           /      |      \
                     팩트 테이블  MV 집계  매치업 집계
```

- **PostgreSQL**: 매치/소환사 원본 데이터 저장 (OLTP)
- **ClickHouse**: 챔피언 통계, 빌드 추천, 매치업 분석 전용 (OLAP)
- 연동 방식: ClickHouse PostgreSQL 외부 테이블 -> `INSERT...SELECT`로 적재

## 1. 컨테이너 실행

```bash
cd docker
docker compose up -d
```

실행 시 자동으로 수행되는 작업:
- `02_local_tables.sql` - 팩트 테이블 생성 (ReplacingMergeTree)
- `03_materialized_views.sql` - 집계 테이블 + Materialized View 생성 (SummingMergeTree)

## 2. ClickHouse 컨테이너 접속

```bash
docker exec -it clickhouse_analytics clickhouse-client
```

이후 모든 SQL 명령은 이 클라이언트 안에서 실행합니다.

## 3. PostgreSQL 소스 테이블 등록

ClickHouse에서 PostgreSQL 데이터를 읽을 수 있도록 외부 테이블을 등록합니다.
`docker/clickhouse/init/01_pg_source_tables.sql`의 내용을 클라이언트에 붙여넣어 실행합니다.

> **주의**: `01_pg_source_tables.sql`에 PostgreSQL 접속 정보(host, user, password)가 하드코딩되어 있습니다.
> 환경에 맞게 수정 후 실행하세요. 이 파일은 `.gitignore`에 의해 git에서 제외됩니다.

### 연결 확인

```sql
SELECT count(*) FROM pg_match;
SELECT count(*) FROM pg_match_summoner;
```

정상적으로 숫자가 조회되면 PostgreSQL 연결이 완료된 것입니다.

## 4. 데이터 적재

PostgreSQL에서 ClickHouse 로컬 테이블로 데이터를 옮깁니다.
`docker/clickhouse/init/04_data_load.sql`의 내용을 클라이언트에 붙여넣어 실행합니다.

이 스크립트는 두 단계로 동작합니다:

1. **match_participant_local 적재** - `pg_match_summoner` + `pg_match`를 조인하여 랭크 솔로 큐(queueId=420) 데이터를 비정규화 후 적재
2. **match_lane_matchup_local 적재** - `match_participant_local`을 자체 조인하여 같은 라인 상대 매치업 데이터 생성

두 테이블 모두 이미 적재된 `match_id`는 건너뛰므로 **반복 실행이 안전**합니다.

적재가 완료되면 Materialized View가 자동으로 집계 테이블을 갱신합니다:
- `champion_stats_local` - 챔피언 기본 통계
- `item_build_stats_local` - 아이템 빌드 통계
- `skill_build_stats_local` - 스킬 빌드 통계
- `rune_build_stats_local` - 룬 빌드 통계
- `champion_matchup_stats_local` - 매치업 통계

## 5. 적재 결과 확인

```sql
-- 팩트 테이블 건수
SELECT count() FROM match_participant_local;
SELECT count() FROM match_lane_matchup_local;

-- 집계 테이블 건수
SELECT count() FROM champion_stats_local;
SELECT count() FROM item_build_stats_local;
SELECT count() FROM skill_build_stats_local;
SELECT count() FROM rune_build_stats_local;
SELECT count() FROM champion_matchup_stats_local;
```

## 6. 분석 쿼리 예시

### 챔피언 승률 통계

```sql
SELECT
    champion_id,
    team_position,
    patch,
    sum(games)                              AS games,
    sum(wins)                               AS wins,
    round(sum(wins) / sum(games) * 100, 2)  AS winrate
FROM champion_stats_local
WHERE patch = '15.3' AND tier = 'GOLD'
GROUP BY champion_id, team_position, patch
ORDER BY games DESC
LIMIT 20;
```

### 아이템 빌드 추천

```sql
SELECT
    items_sorted,
    sum(games)                              AS games,
    sum(wins)                               AS wins,
    round(sum(wins) / sum(games) * 100, 2)  AS winrate
FROM item_build_stats_local
WHERE champion_id = 266
  AND team_position = 'TOP'
  AND patch = '15.3'
GROUP BY items_sorted
ORDER BY games DESC
LIMIT 10;
```

### 스킬 빌드 추천

```sql
SELECT
    skill_order_15,
    sum(games)                              AS games,
    sum(wins)                               AS wins,
    round(sum(wins) / sum(games) * 100, 2)  AS winrate
FROM skill_build_stats_local
WHERE champion_id = 266
  AND team_position = 'TOP'
  AND patch = '15.3'
GROUP BY skill_order_15
ORDER BY games DESC
LIMIT 5;
```

### 챔피언 매치업 상성

```sql
SELECT
    opponent_champion_id,
    sum(games)                              AS games,
    sum(wins)                               AS wins,
    round(sum(wins) / sum(games) * 100, 2)  AS winrate
FROM champion_matchup_stats_local
WHERE champion_id = 266
  AND team_position = 'TOP'
  AND patch = '15.3'
GROUP BY opponent_champion_id
ORDER BY games DESC
LIMIT 10;
```

## 7. 증분 적재 (데이터 갱신)

PostgreSQL에 새로운 매치 데이터가 쌓이면 `04_data_load.sql`의 내용을 다시 실행합니다.
이미 적재된 `match_id`는 `NOT IN` 조건으로 건너뛰므로 신규 데이터만 추가됩니다.

## 테이블 구조 요약

| 테이블 | 엔진 | 용도 |
|-------|------|------|
| `pg_match` | PostgreSQL (외부) | 매치 기본 정보 읽기 |
| `pg_match_summoner` | PostgreSQL (외부) | 참가자 정보 읽기 |
| `pg_skill_events` | PostgreSQL (외부) | 스킬 이벤트 읽기 (VIEW) |
| `pg_item_events` | PostgreSQL (외부) | 아이템 이벤트 읽기 (VIEW) |
| `match_participant_local` | ReplacingMergeTree | 핵심 팩트 테이블 (patch 파티션) |
| `match_lane_matchup_local` | ReplacingMergeTree | 라인 매치업 팩트 테이블 |
| `champion_stats_local` | SummingMergeTree | 챔피언 기본 통계 집계 |
| `item_build_stats_local` | SummingMergeTree | 아이템 빌드 집계 |
| `skill_build_stats_local` | SummingMergeTree | 스킬 빌드 집계 |
| `rune_build_stats_local` | SummingMergeTree | 룬 빌드 집계 |
| `champion_matchup_stats_local` | SummingMergeTree | 매치업 집계 |

## 초기화 SQL 파일 목록

| 파일 | 자동 실행 | 설명 |
|------|----------|------|
| `01_pg_source_tables.sql` | X | PostgreSQL 외부 테이블 (접속 정보 포함) |
| `02_local_tables.sql` | O | 로컬 팩트 테이블 DDL |
| `03_materialized_views.sql` | O | 집계 테이블 + Materialized View |
| `04_data_load.sql` | X | INSERT...SELECT 데이터 적재 |
