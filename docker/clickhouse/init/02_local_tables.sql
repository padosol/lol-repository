-- =====================================================
-- 02: ReplacingMergeTree 팩트 테이블 (컨테이너 시작 시 실행)
-- =====================================================

-- match + match_participant 비정규화 팩트 테이블
CREATE TABLE IF NOT EXISTS match_participant_local
(
    match_id          String,
    champion_id       Int32,
    team_position     LowCardinality(String),
    team_id           Int32,
    win               UInt8,
    queue_id          Int32,
    platform_id       LowCardinality(String),
    patch_version     LowCardinality(String),
    tier              LowCardinality(String),
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
    summoner1id       Int32,
    summoner2id       Int32
)
ENGINE = ReplacingMergeTree()
PARTITION BY patch_version
ORDER BY (champion_id, team_position, match_id, team_id);

-- match + match_ban 비정규화 팩트 테이블
CREATE TABLE IF NOT EXISTS match_ban_local
(
    match_id      String,
    champion_id   Int32,
    team_id       Int32,
    pick_turn     Int32,
    queue_id      Int32,
    platform_id   LowCardinality(String),
    patch_version LowCardinality(String),
    tier          LowCardinality(String)
)
ENGINE = ReplacingMergeTree()
PARTITION BY patch_version
ORDER BY (champion_id, match_id, team_id, pick_turn);

-- match + skill_level_up_event 비정규화 팩트 테이블
CREATE TABLE IF NOT EXISTS match_skill_build_local
(
    match_id      String,
    champion_id   Int32,
    team_position LowCardinality(String),
    win           UInt8,
    queue_id      Int32,
    platform_id   LowCardinality(String),
    patch_version LowCardinality(String),
    tier          LowCardinality(String),
    skill_build   LowCardinality(String)    -- '1,3,2,1,1,4,...' (첫 15레벨 skill_slot 시퀀스)
)
ENGINE = ReplacingMergeTree()
PARTITION BY patch_version
ORDER BY (champion_id, team_position, match_id, skill_build);

-- match + item_event 비정규화 팩트 테이블 (시작 아이템 빌드)
CREATE TABLE IF NOT EXISTS match_start_item_build_local
(
    match_id      String,
    champion_id   Int32,
    team_position LowCardinality(String),
    win           UInt8,
    queue_id      Int32,
    platform_id   LowCardinality(String),
    patch_version LowCardinality(String),
    tier          LowCardinality(String),
    start_items   LowCardinality(String)    -- '1055,2003,2003' (item_id 정렬, 콤마 구분)
)
ENGINE = ReplacingMergeTree()
PARTITION BY patch_version
ORDER BY (champion_id, team_position, match_id, start_items);
