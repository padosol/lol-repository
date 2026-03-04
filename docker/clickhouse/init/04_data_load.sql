-- =====================================================
-- 04: PostgreSQL → ClickHouse 데이터 적재 (수동 실행)
-- =====================================================
-- 01_pg_source_tables.sql 실행 후 사용하세요.
-- 증분 로드: WHERE 절에 patch_version 필터를 추가하여 특정 패치만 로드 가능
--   예) AND m.patch_version = '15.1'
-- =====================================================

-- match_participant_local 적재
INSERT INTO match_participant_local
SELECT
    m.match_id,
    mp.champion_id,
    mp.team_position,
    mp.team_id,
    mp.win,
    m.queue_id,
    m.platform_id,
    m.patch_version,
    assumeNotNull(mp.tier) AS tier
FROM pg_match AS m
INNER JOIN pg_match_participant AS mp ON m.match_id = mp.match_id
WHERE m.queue_id = 420
  AND mp.tier IS NOT NULL
  AND m.patch_version IS NOT NULL
  AND mp.team_position != '';

-- match_ban_local 적재
INSERT INTO match_ban_local
SELECT
    m.match_id,
    mb.champion_id,
    mb.team_id,
    mb.pick_turn,
    m.queue_id,
    m.platform_id,
    m.patch_version,
    assumeNotNull(mp.tier) AS tier
FROM pg_match AS m
INNER JOIN pg_match_ban AS mb ON m.match_id = mb.match_id
INNER JOIN pg_match_participant AS mp
    ON mb.match_id = mp.match_id
   AND mp.participant_id = mb.pick_turn
WHERE m.queue_id = 420
  AND mp.tier IS NOT NULL
  AND m.patch_version IS NOT NULL;
