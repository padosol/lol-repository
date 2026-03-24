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
WHERE queue_id IN (420, 440)
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
WHERE queue_id IN (420, 440)
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
WHERE queue_id IN (420, 440)
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
WHERE queue_id IN (420, 440) AND team_position != ''
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
WHERE queue_id IN (420, 440) AND team_position != ''
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
WHERE queue_id IN (420, 440) AND team_position != ''
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
WHERE queue_id IN (420, 440) AND team_position != ''
GROUP BY patch_version, platform_id, tier, champion_id, team_position, start_items;

-- 8. 챔피언 3코어 아이템 빌드 순서 집계 (라인별 빌드 순서 승률/픽률)
CREATE TABLE IF NOT EXISTS champion_item_build_stats_agg
(
    patch_version LowCardinality(String),
    platform_id   LowCardinality(String),
    tier          LowCardinality(String),
    champion_id   Int32,
    team_position LowCardinality(String),
    item_build    LowCardinality(String),
    games         UInt64,
    wins          UInt64
)
ENGINE = SummingMergeTree((games, wins))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, champion_id, team_position, item_build);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_champion_item_build_stats
TO champion_item_build_stats_agg
AS
SELECT
    patch_version, platform_id, tier, champion_id, team_position,
    item_build,
    count()  AS games,
    sum(win) AS wins
FROM match_item_build_local
WHERE queue_id IN (420, 440) AND team_position != ''
GROUP BY patch_version, platform_id, tier, champion_id, team_position, item_build;

-- 9. 챔피언 완성 아이템 집계 (라인별 코어 순서별 아이템 승률/픽률)
CREATE TABLE IF NOT EXISTS champion_item_stats_agg
(
    patch_version LowCardinality(String),
    platform_id   LowCardinality(String),
    tier          LowCardinality(String),
    champion_id   Int32,
    team_position LowCardinality(String),
    item_id       Int32,
    item_order    UInt8,
    games         UInt64,
    wins          UInt64
)
ENGINE = SummingMergeTree((games, wins))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, champion_id, team_position, item_id, item_order);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_champion_item_stats
TO champion_item_stats_agg
AS
SELECT
    patch_version, platform_id, tier, champion_id, team_position,
    item_id, item_order,
    count()  AS games,
    sum(win) AS wins
FROM match_final_item_local
WHERE queue_id IN (420, 440) AND team_position != ''
GROUP BY patch_version, platform_id, tier, champion_id, team_position, item_id, item_order;

-- 10. 챔피언 매치업 집계 (라인별 상대 챔피언 승률)
CREATE TABLE IF NOT EXISTS champion_matchup_stats_agg
(
    patch_version        LowCardinality(String),
    platform_id          LowCardinality(String),
    tier                 LowCardinality(String),
    champion_id          Int32,
    team_position        LowCardinality(String),
    opponent_champion_id Int32,
    games                UInt64,
    wins                 UInt64
)
ENGINE = SummingMergeTree((games, wins))
PARTITION BY patch_version
ORDER BY (patch_version, platform_id, tier, champion_id, team_position, opponent_champion_id);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_champion_matchup_stats
TO champion_matchup_stats_agg
AS
SELECT
    patch_version, platform_id, tier,
    champion_id, team_position, opponent_champion_id,
    count()  AS games,
    sum(win) AS wins
FROM match_matchup_local
WHERE queue_id IN (420, 440) AND team_position != ''
GROUP BY patch_version, platform_id, tier, champion_id, team_position, opponent_champion_id;
