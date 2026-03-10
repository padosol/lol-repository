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

-- 전설급 아이템 ID 참조 테이블 (패치마다 Data Dragon에서 업데이트)
CREATE TABLE IF NOT EXISTS legendary_items
(
    item_id   Int32,
    item_name String
)
ENGINE = MergeTree()
ORDER BY item_id;

-- 전설급 3코어 아이템 빌드 순서 팩트 테이블 (참가자당 1행)
-- item_build: 구매 순서대로 3코어 아이템 ID (예: '3078,6632,3071')
CREATE TABLE IF NOT EXISTS match_item_build_local
(
    match_id      String,
    champion_id   Int32,
    team_position LowCardinality(String),
    win           UInt8,
    queue_id      Int32,
    platform_id   LowCardinality(String),
    patch_version LowCardinality(String),
    tier          LowCardinality(String),
    item_build    LowCardinality(String)
)
ENGINE = ReplacingMergeTree()
PARTITION BY patch_version
ORDER BY (champion_id, team_position, match_id, item_build);

-- 게임 종료 시 전설급 아이템 팩트 테이블 (참가자당 최대 6행, 비전설급 제외)
-- item_order: 코어 순서 (1=1코어, 2=2코어, 3=3코어...), item_event 구매 타임스탬프 기준
CREATE TABLE IF NOT EXISTS match_final_item_local
(
    match_id      String,
    champion_id   Int32,
    team_position LowCardinality(String),
    win           UInt8,
    queue_id      Int32,
    platform_id   LowCardinality(String),
    patch_version LowCardinality(String),
    tier          LowCardinality(String),
    item_id       Int32,
    item_order    UInt8
)
ENGINE = ReplacingMergeTree()
PARTITION BY patch_version
ORDER BY (champion_id, team_position, item_order, match_id);

-- 같은 라인 상대 챔피언 매치업 팩트 테이블 (참가자당 1행)
CREATE TABLE IF NOT EXISTS match_matchup_local
(
    match_id             String,
    champion_id          Int32,
    opponent_champion_id Int32,
    team_position        LowCardinality(String),
    win                  UInt8,
    queue_id             Int32,
    platform_id          LowCardinality(String),
    patch_version        LowCardinality(String),
    tier                 LowCardinality(String)
)
ENGINE = ReplacingMergeTree()
PARTITION BY patch_version
ORDER BY (champion_id, team_position, match_id, opponent_champion_id);
