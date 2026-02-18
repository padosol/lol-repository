# 챔피언별 통계 시스템 설계

## 개요

op.gg, deeplol.gg 같은 챔피언 빌드 페이지를 위한 통계 집계 기능이다. 현재 `match_summoner`, `skill_events`, `item_events`, `match_team` 테이블에 원본 매치 데이터가 충분히 저장되어 있으나, 이를 챔피언 단위로 집계하는 로직은 전혀 없다. **사전 집계(Pre-aggregation) 테이블 + 수동 트리거** 방식으로 통계를 생성한다.

---

## 1. 사전 집계 전략

### 왜 사전 집계인가?
- `match_summoner` 테이블이 수백만 행 이상으로 증가 → 실시간 GROUP BY 쿼리는 비실용적
- 기존 `SummonerRankingCalculationService`도 동일한 집계 테이블 패턴 사용 중

### 트리거 방식
- **수동 트리거**: 스케줄러 없이 API 호출 또는 관리 커맨드로 집계 실행
- 추후 필요시 `@Scheduled` 추가 가능

### 집계 차원 (필터 조건 = 테이블 공통 컬럼)

| 컬럼 | 설명 | 예시 |
|------|------|------|
| `champion_id` | 챔피언 | 236 (루시안) |
| `team_position` | 라인 | TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY |
| `season` | 시즌 | 26 |
| `tier_group` | 티어 그룹 | ALL, EMERALD_PLUS, DIAMOND_PLUS, MASTER_PLUS |
| `platform_id` | 지역 | KR, ALL |
| `queue_id` | 게임 종류 | 420 (솔로랭크) |
| `game_version` | 패치 | "15.3" (major.minor) |

---

## 2. 티어 처리 (핵심 설계 결정)

### 문제
- `match_summoner`에 티어 정보가 없음
- 매치 저장 시점에 `league_summoner`에 해당 유저가 없을 수 있음 (미등록 소환사)
- 매번 RIOT API를 호출하면 rate limit 부족 가능

### 해결: 집계 시점에 `league_summoner` JOIN

`match_summoner`에 tier 컬럼을 추가하지 않는다. 대신 **집계 쿼리 실행 시점에** `league_summoner` 테이블과 JOIN하여 각 플레이어의 현재 티어를 기준으로 필터링한다.

```sql
-- 집계 시 tier 적용 예시
SELECT ms.champion_id, ms.team_position, ...
FROM match_summoner ms
JOIN match m ON ms.match_id = m.match_id
LEFT JOIN league_summoner ls ON ms.puuid = ls.puuid AND ls.queue = 'RANKED_SOLO_5x5'
WHERE m.season = :season AND m.queue_id = :queueId
  AND (
    :tierGroup = 'ALL'                           -- ALL: 티어 무관 전체 포함
    OR ls.tier IN (:tiers)                       -- 특정 티어 그룹: 등록된 유저만 필터
  )
GROUP BY ms.champion_id, ms.team_position
```

**장점**:
- `match_summoner` 스키마 변경 불필요
- RIOT API 추가 호출 불필요
- 집계 시점의 최신 티어 반영

**단점**:
- `league_summoner`에 없는 플레이어는 특정 티어 필터에서 제외됨 (ALL에서는 포함)
- 과거 매치에 현재 티어가 적용됨 (동일 시즌 내에서는 큰 차이 없음)

**`match_summoner` 스키마 변경 없음** - 기존 파일 수정 최소화

---

## 3. 아이템 메타데이터 테이블

### `item_metadata` 테이블
아이템 분류(시작 아이템, 부츠, 전설 아이템 등)를 DB 테이블로 관리한다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `item_id` | INT (PK) | RIOT 아이템 ID |
| `item_name` | VARCHAR | 아이템 이름 |
| `item_category` | VARCHAR | STARTER, BOOTS, LEGENDARY, CONSUMABLE, TRINKET |
| `game_version` | VARCHAR | 적용 패치 (NULL이면 전 패치 공통) |

관리 API를 통해 패치마다 아이템 분류 데이터를 업데이트한다.

---

## 4. 집계 테이블 설계 (6개)

### 4-1. `champion_stat_summary` - 챔피언 기본 통계

| 컬럼 | 타입 | 설명 |
|------|------|------|
| 차원 컬럼들 | - | 위 공통 컬럼 7개 |
| `total_games` | INT | 판수 |
| `wins` | INT | 승수 |
| `total_bans` | INT | 밴 수 (`match_team`에서 집계) |
| `total_matches_in_dimension` | INT | 해당 차원 전체 판수 (픽률 계산용) |
| `avg_kills` | DECIMAL | 평균 킬 |
| `avg_deaths` | DECIMAL | 평균 데스 |
| `avg_assists` | DECIMAL | 평균 어시스트 |
| `avg_cs` | DECIMAL | 평균 CS |
| `avg_gold` | DECIMAL | 평균 골드 |

### 4-2. `champion_rune_stat` - 룬 조합 통계

| 추가 컬럼 | 설명 |
|-----------|------|
| `primary_rune_id` | 주 룬 트리 (ex: 8400 결의) |
| `primary_rune_ids` | 주 룬 세부 선택 CSV (ex: "8437,8446,8444,8451") |
| `secondary_rune_id` | 보조 룬 트리 |
| `secondary_rune_ids` | 보조 룬 세부 선택 CSV |
| `stat_offense/flex/defense` | 능력치 파편 |
| `games`, `wins` | 판수, 승수 |

**데이터 소스**: `match_summoner`의 `StyleValue` (primaryRuneId, primaryRuneIds, secondaryRuneId, secondaryRuneIds) + `StatValue` (offense, flex, defense)

### 4-3. `champion_spell_stat` - 소환사 주문 통계

| 추가 컬럼 | 설명 |
|-----------|------|
| `spell1_id` | 주문1 (작은 ID) |
| `spell2_id` | 주문2 (큰 ID) |
| `games`, `wins` | |

`LEAST(summoner1Id, summoner2Id)`, `GREATEST(...)` 로 정규화하여 Flash+점화 vs 점화+Flash 중복 방지

### 4-4. `champion_skill_stat` - 스킬 빌드 통계

| 추가 컬럼 | 설명 |
|-----------|------|
| `skill_order` | 전체 레벨업 순서 CSV (ex: "1,3,1,2,1,3,...") |
| `skill_priority` | 우선순위 요약 (ex: "Q>E>W") |
| `games`, `wins` | |

**데이터 소스**: `skill_events` 테이블
- `skill_events`에서 match_id + participantId별 timestamp 순 정렬, `levelUpType = 'NORMAL'`만 추출
- `match_summoner`와 match_id + participantId로 JOIN → 챔피언/포지션/승패 매핑
- `skill_priority` 계산: R(4번 슬롯) 제외, 먼저 5레벨 도달하는 스킬 순

### 4-5. `champion_item_stat` - 아이템 빌드 통계

| 추가 컬럼 | 설명 |
|-----------|------|
| `build_type` | STARTER / BOOTS / CORE |
| `item_ids` | 정렬된 아이템 ID CSV |
| `games`, `wins` | |

**분류 전략** (`item_metadata` 테이블 활용):
- **STARTER**: `item_events`에서 timestamp < 90초 AND `type = ITEM_PURCHASED`, `item_metadata.item_category = 'STARTER'`
- **BOOTS**: `match_summoner`의 item0~item6 중 `item_metadata.item_category = 'BOOTS'`
- **CORE**: item0~item6에서 `item_metadata.item_category = 'LEGENDARY'`인 아이템 조합

### 4-6. `champion_matchup_stat` - 상대 챔피언 매치업

| 추가 컬럼 | 설명 |
|-----------|------|
| `opponent_champion_id` | 상대 챔피언 |
| `games`, `wins` | |
| `avg_kills/deaths/assists` | 평균 지표 |
| `avg_gold_diff` | 상대 대비 골드 차이 |

**핵심 알고리즘** - `match_summoner` 셀프 조인:
```sql
SELECT a.champion_id, a.team_position, b.champion_id as opponent_champion_id,
       COUNT(*), SUM(CASE WHEN a.win THEN 1 ELSE 0 END),
       AVG(a.gold_earned - b.gold_earned) as avg_gold_diff
FROM match_summoner a
JOIN match_summoner b ON a.match_id = b.match_id
     AND a.team_position = b.team_position AND a.team_id != b.team_id
JOIN match m ON a.match_id = m.match_id
LEFT JOIN league_summoner ls ON a.puuid = ls.puuid AND ls.queue = 'RANKED_SOLO_5x5'
WHERE m.season = :season AND m.queue_id = :queueId
  AND a.team_position != '' AND b.team_position != ''
  AND (:tierGroup = 'ALL' OR ls.tier IN (:tiers))
GROUP BY a.champion_id, a.team_position, b.champion_id
```

---

## 5. 새로 생성할 파일 목록

### Enum (core/enum)
- `Position.java` - TOP, JUNGLE, MIDDLE, BOTTOM, UTILITY
- `TierGroup.java` - ALL, EMERALD_PLUS, DIAMOND_PLUS, MASTER_PLUS 등 (각 그룹에 포함 tier 목록 보유)

### Domain (core/domain)
```
com.mmrtr.lol.domain.champion_stat/
├── domain/
│   ├── ChampionStatSummary.java
│   ├── ChampionRuneStat.java
│   ├── ChampionSpellStat.java
│   ├── ChampionSkillStat.java
│   ├── ChampionItemStat.java
│   └── ChampionMatchupStat.java
├── repository/
│   ├── ChampionStatSummaryRepositoryPort.java
│   ├── ChampionRuneStatRepositoryPort.java
│   └── ... (각 통계별 Port)
└── service/
    └── ChampionStatQueryUseCase.java
```

### Persistence (infra/persistence)
```
com.mmrtr.lol.infra.persistence.champion_stat/
├── entity/
│   ├── ChampionStatSummaryEntity.java
│   ├── ChampionRuneStatEntity.java
│   ├── ChampionItemStatEntity.java
│   ├── ChampionSpellStatEntity.java
│   ├── ChampionSkillStatEntity.java
│   ├── ChampionMatchupStatEntity.java
│   └── ItemMetadataEntity.java
├── repository/
│   ├── ChampionStatSummaryJpaRepository.java
│   ├── ChampionStatSummaryRepositoryImpl.java
│   ├── ItemMetadataJpaRepository.java
│   └── ... (각 통계별 JPA + Impl)
└── service/
    ├── ChampionStatAggregationService.java    (집계 로직 - 수동 트리거)
    └── ChampionStatQueryService.java          (API 조회 서비스)
```

### API (infra/api)
```
com.mmrtr.lol.controller.champion/
├── ChampionStatController.java           (조회 API)
├── ChampionStatAdminController.java      (집계 트리거 + 아이템 메타 관리 API)
└── response/
    ├── ChampionStatSummaryResponse.java
    ├── ChampionRuneStatResponse.java
    ├── ChampionSpellStatResponse.java
    ├── ChampionSkillStatResponse.java
    ├── ChampionItemStatResponse.java
    └── ChampionMatchupStatResponse.java
```

### 기존 파일 수정 없음
- 티어를 집계 시 JOIN으로 처리하므로 `MatchSummonerEntity`, `MatchService` 등 기존 코드 변경 불필요

---

## 6. API 엔드포인트

### 조회 API
```
GET /api/riot/champions/{championId}/stats/summary
GET /api/riot/champions/{championId}/stats/runes
GET /api/riot/champions/{championId}/stats/spells
GET /api/riot/champions/{championId}/stats/skills
GET /api/riot/champions/{championId}/stats/items
GET /api/riot/champions/{championId}/stats/matchups
```
**공통 Query Parameters**: `position`, `season`, `tierGroup`, `platform`, `queueId`, `patch`

### 관리 API
```
POST /api/admin/champion-stats/aggregate          ← 집계 수동 실행
POST /api/admin/item-metadata                     ← 아이템 메타데이터 등록/수정
GET  /api/admin/item-metadata                     ← 아이템 메타데이터 조회
```

### Redis 캐싱
키 패턴: `champion_stat:{type}:{championId}:{position}:{season}:{tierGroup}:{platform}:{queueId}:{patch}`
TTL: 수동 트리거이므로 집계 실행 시 관련 캐시 무효화

---

## 7. 구현 순서

1. **Enum 추가**: `Position`, `TierGroup`
2. **아이템 메타데이터**: `item_metadata` 테이블 + Entity + Repository + 관리 API
3. **집계 테이블 DDL**: 6개 테이블 생성
4. **Domain 레이어**: 도메인 객체 + Port 인터페이스
5. **Persistence 레이어**: Entity + Repository (NamedParameterJdbcTemplate 벌크 삽입)
6. **집계 서비스**: `ChampionStatAggregationService` (네이티브 SQL로 집계)
7. **조회 서비스 + API**: Controller + Response DTO + 조회 서비스
8. **Redis 캐싱**: 조회 결과 캐싱 + 집계 시 캐시 무효화

---

## 8. 재사용할 기존 코드

| 기존 코드 | 용도 |
|-----------|------|
| `SummonerRankingCalculationService` 패턴 | 집계 서비스 구조 참고 |
| `MatchRepositoryImpl.bulkSave()` + `NamedParameterJdbcTemplate` | 집계 결과 벌크 삽입 패턴 |
| `Tier` enum | 티어 그룹 정의 시 참조 |
| `Platform` enum | 지역 필터 검증 |
| `RedisTemplate` 설정 | 캐싱 인프라 |
| `SummonerController` 패턴 | API 컨트롤러 구조 |

---

## 9. 필요 인덱스

```sql
-- 집계 쿼리 성능을 위한 인덱스 (기존 테이블)
CREATE INDEX idx_ms_match_champ_pos ON match_summoner(match_id, champion_id, team_position);
CREATE INDEX idx_ms_champ_pos ON match_summoner(champion_id, team_position);
CREATE INDEX idx_match_season_queue ON match(season, queue_id);
CREATE INDEX idx_ls_puuid_queue ON league_summoner(puuid, queue);  -- 이미 존재할 수 있음

-- 집계 테이블 조회용 (각 테이블에 동일 패턴)
CREATE UNIQUE INDEX idx_stat_summary_dim ON champion_stat_summary(
  champion_id, team_position, season, tier_group, platform_id, queue_id, game_version
);
```

---

## 10. 검증 방법

1. **빌드**: `./gradlew build` 전체 빌드 통과
2. **단위 테스트**: 스킬 우선순위 계산, 주문 정규화, 아이템 분류 로직
3. **통합 테스트**: 테스트 데이터로 집계 서비스 실행 → 집계 테이블 결과 검증
4. **API 테스트**: 각 엔드포인트 호출 → 응답 구조 및 수치 확인
