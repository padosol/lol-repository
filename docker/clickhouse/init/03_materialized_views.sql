-- =====================================================
-- 03: SummingMergeTree 집계 테이블 + Materialized Views (컨테이너 시작 시 실행)
-- =====================================================

-- 1. 챔피언 통계 집계 (승률/픽률)
CREATE TABLE IF NOT EXISTS champion_stats_agg
(
    patch_version LowCardinality(String),
    platform_id   LowCardinality(String),
    tier          LowCardinality(String),
    champion_id   Int32,
    team_position LowCardinality(String),
    games         UInt64,
    wins          UInt64
)
ENGINE = SummingMergeTree((games, wins))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, champion_id, team_position);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_champion_stats
TO champion_stats_agg
AS
SELECT
    patch_version,
    platform_id,
    tier,
    champion_id,
    team_position,
    count()    AS games,
    sum(win)   AS wins
FROM match_participant_local
WHERE queue_id = 420
  AND team_position != ''
GROUP BY patch_version, platform_id, tier, champion_id, team_position;

-- 2. 챔피언 밴 집계 (밴률)
CREATE TABLE IF NOT EXISTS champion_bans_agg
(
    patch_version LowCardinality(String),
    platform_id   LowCardinality(String),
    tier          LowCardinality(String),
    champion_id   Int32,
    bans          UInt64
)
ENGINE = SummingMergeTree((bans,))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, champion_id);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_champion_bans
TO champion_bans_agg
AS
SELECT
    patch_version,
    platform_id,
    tier,
    champion_id,
    count() AS bans
FROM match_ban_local
WHERE queue_id = 420
  AND champion_id > 0
GROUP BY patch_version, platform_id, tier, champion_id;

-- 3. 매치 수 집계 (픽률/밴률 분모)
CREATE TABLE IF NOT EXISTS match_count_agg
(
    patch_version    LowCardinality(String),
    platform_id      LowCardinality(String),
    tier             LowCardinality(String),
    team_position    LowCardinality(String),
    participant_rows UInt64
)
ENGINE = SummingMergeTree((participant_rows,))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, team_position);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_match_count
TO match_count_agg
AS
SELECT
    patch_version,
    platform_id,
    tier,
    team_position,
    count() AS participant_rows
FROM match_participant_local
WHERE queue_id = 420
  AND team_position != ''
GROUP BY patch_version, platform_id, tier, team_position;
