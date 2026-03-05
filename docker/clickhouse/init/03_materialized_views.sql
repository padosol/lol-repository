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

-- 4. 챔피언 룬 집계 (라인별 룬 페이지 승률/픽률)
CREATE TABLE IF NOT EXISTS champion_rune_stats_agg
(
    patch_version     LowCardinality(String),
    platform_id       LowCardinality(String),
    tier              LowCardinality(String),
    champion_id       Int32,
    team_position     LowCardinality(String),
    primary_style_id  Int32,
    primary_perk0     Int32,
    primary_perk1     Int32,
    primary_perk2     Int32,
    primary_perk3     Int32,
    sub_style_id      Int32,
    sub_perk0         Int32,
    sub_perk1         Int32,
    stat_perk_defense Int32,
    stat_perk_flex    Int32,
    stat_perk_offense Int32,
    games             UInt64,
    wins              UInt64
)
ENGINE = SummingMergeTree((games, wins))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, champion_id, team_position,
          primary_style_id, primary_perk0, primary_perk1, primary_perk2, primary_perk3,
          sub_style_id, sub_perk0, sub_perk1,
          stat_perk_defense, stat_perk_flex, stat_perk_offense);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_champion_rune_stats
TO champion_rune_stats_agg
AS
SELECT
    patch_version, platform_id, tier, champion_id, team_position,
    primary_style_id, primary_perk0, primary_perk1, primary_perk2, primary_perk3,
    sub_style_id, sub_perk0, sub_perk1,
    stat_perk_defense, stat_perk_flex, stat_perk_offense,
    count()  AS games,
    sum(win) AS wins
FROM match_participant_local
WHERE queue_id = 420 AND team_position != ''
GROUP BY patch_version, platform_id, tier, champion_id, team_position,
         primary_style_id, primary_perk0, primary_perk1, primary_perk2, primary_perk3,
         sub_style_id, sub_perk0, sub_perk1,
         stat_perk_defense, stat_perk_flex, stat_perk_offense;

-- 5. 챔피언 소환사 주문 집계 (라인별 소환사 주문 조합 승률/픽률)
CREATE TABLE IF NOT EXISTS champion_spell_stats_agg
(
    patch_version LowCardinality(String),
    platform_id   LowCardinality(String),
    tier          LowCardinality(String),
    champion_id   Int32,
    team_position LowCardinality(String),
    summoner1id   Int32,
    summoner2id   Int32,
    games         UInt64,
    wins          UInt64
)
ENGINE = SummingMergeTree((games, wins))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, champion_id, team_position,
          summoner1id, summoner2id);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_champion_spell_stats
TO champion_spell_stats_agg
AS
SELECT
    patch_version, platform_id, tier, champion_id, team_position,
    summoner1id, summoner2id,
    count()  AS games,
    sum(win) AS wins
FROM match_participant_local
WHERE queue_id = 420 AND team_position != ''
GROUP BY patch_version, platform_id, tier, champion_id, team_position,
         summoner1id, summoner2id;

-- 6. 챔피언 스킬빌드 집계 (라인별 스킬빌드 승률/픽률)
CREATE TABLE IF NOT EXISTS champion_skill_build_stats_agg
(
    patch_version LowCardinality(String),
    platform_id   LowCardinality(String),
    tier          LowCardinality(String),
    champion_id   Int32,
    team_position LowCardinality(String),
    skill_build   LowCardinality(String),
    games         UInt64,
    wins          UInt64
)
ENGINE = SummingMergeTree((games, wins))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, champion_id, team_position, skill_build);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_champion_skill_build_stats
TO champion_skill_build_stats_agg
AS
SELECT
    patch_version, platform_id, tier, champion_id, team_position,
    skill_build,
    count()  AS games,
    sum(win) AS wins
FROM match_skill_build_local
WHERE queue_id = 420 AND team_position != ''
GROUP BY patch_version, platform_id, tier, champion_id, team_position, skill_build;

-- 7. 챔피언 시작 아이템 빌드 집계 (라인별 시작 아이템 승률/픽률)
CREATE TABLE IF NOT EXISTS champion_start_item_stats_agg
(
    patch_version LowCardinality(String),
    platform_id   LowCardinality(String),
    tier          LowCardinality(String),
    champion_id   Int32,
    team_position LowCardinality(String),
    start_items   LowCardinality(String),
    games         UInt64,
    wins          UInt64
)
ENGINE = SummingMergeTree((games, wins))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, champion_id, team_position, start_items);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_champion_start_item_stats
TO champion_start_item_stats_agg
AS
SELECT
    patch_version, platform_id, tier, champion_id, team_position,
    start_items,
    count()  AS games,
    sum(win) AS wins
FROM match_start_item_build_local
WHERE queue_id = 420 AND team_position != ''
GROUP BY patch_version, platform_id, tier, champion_id, team_position, start_items;
