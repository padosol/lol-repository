-- =====================================================
-- 02: ReplacingMergeTree 팩트 테이블 (컨테이너 시작 시 실행)
-- =====================================================

-- match + match_participant 비정규화 팩트 테이블
CREATE TABLE IF NOT EXISTS match_participant_local
(
    match_id      String,
    champion_id   Int32,
    team_position LowCardinality(String),
    team_id       Int32,
    win           UInt8,
    queue_id      Int32,
    platform_id   LowCardinality(String),
    patch_version LowCardinality(String),
    tier          LowCardinality(String)
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
