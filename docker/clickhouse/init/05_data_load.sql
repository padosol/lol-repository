-- =====================================================
-- 05: PostgreSQL → ClickHouse 데이터 적재 (수동 실행)
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
    assumeNotNull(mp.tier) AS tier,
    assumeNotNull(mp.primary_style_id) AS primary_style_id,
    assumeNotNull(mp.primary_perk0) AS primary_perk0,
    assumeNotNull(mp.primary_perk1) AS primary_perk1,
    assumeNotNull(mp.primary_perk2) AS primary_perk2,
    assumeNotNull(mp.primary_perk3) AS primary_perk3,
    assumeNotNull(mp.sub_style_id) AS sub_style_id,
    assumeNotNull(mp.sub_perk0) AS sub_perk0,
    assumeNotNull(mp.sub_perk1) AS sub_perk1,
    assumeNotNull(mp.stat_perk_defense) AS stat_perk_defense,
    assumeNotNull(mp.stat_perk_flex) AS stat_perk_flex,
    assumeNotNull(mp.stat_perk_offense) AS stat_perk_offense,
    mp.summoner1id,
    mp.summoner2id
FROM pg_match AS m
INNER JOIN pg_match_participant AS mp ON m.match_id = mp.match_id
WHERE m.queue_id = 420
  AND mp.tier IS NOT NULL
  AND m.patch_version IS NOT NULL
  AND mp.team_position != ''
  AND m.match_id NOT IN (SELECT DISTINCT match_id FROM match_participant_local);

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
  AND m.patch_version IS NOT NULL
  AND m.match_id NOT IN (SELECT DISTINCT match_id FROM match_ban_local);

-- match_skill_build_local 적재
INSERT INTO match_skill_build_local
SELECT
    sub.match_id,
    sub.champion_id,
    sub.team_position,
    sub.win,
    sub.queue_id,
    sub.platform_id,
    sub.patch_version,
    sub.tier,
    sub.skill_build
FROM (
    SELECT
        ordered.match_id,
        ordered.champion_id,
        ordered.team_position,
        ordered.win,
        ordered.queue_id,
        ordered.platform_id,
        ordered.patch_version,
        ordered.tier,
        ordered.participant_id,
        arrayStringConcat(
            arraySlice(
                groupArray(toString(ordered.skill_slot)),
                1, 15
            ),
            ','
        ) AS skill_build
    FROM (
        SELECT
            sk.match_id AS match_id,
            sk.participant_id AS participant_id,
            sk.skill_slot AS skill_slot,
            sk.timestamp AS timestamp,
            mp.champion_id AS champion_id,
            mp.team_position AS team_position,
            mp.win AS win,
            m.queue_id AS queue_id,
            m.platform_id AS platform_id,
            m.patch_version AS patch_version,
            assumeNotNull(mp.tier) AS tier
        FROM pg_skill_events_flat AS sk
        INNER JOIN pg_match_participant AS mp
            ON sk.match_id = mp.match_id
           AND sk.participant_id = mp.participant_id
        INNER JOIN pg_match AS m
            ON sk.match_id = m.match_id
        WHERE m.queue_id = 420
          AND mp.tier IS NOT NULL
          AND m.patch_version IS NOT NULL
          AND mp.team_position != ''
          AND sk.match_id NOT IN (SELECT DISTINCT match_id FROM match_skill_build_local)
        ORDER BY sk.match_id, sk.participant_id, sk.timestamp ASC
    ) AS ordered
    GROUP BY
        ordered.match_id, ordered.champion_id, ordered.team_position,
        ordered.win, ordered.queue_id, ordered.platform_id, ordered.patch_version,
        ordered.tier, ordered.participant_id
    HAVING count(*) >= 15
) AS sub;

-- match_start_item_build_local 적재 (ITEM_UNDO 처리 포함)
INSERT INTO match_start_item_build_local
SELECT
    agg.match_id, agg.champion_id, agg.team_position, agg.win,
    agg.queue_id, agg.platform_id, agg.patch_version, agg.tier,
    agg.start_items
FROM (
    SELECT
        net.match_id, net.champion_id, net.team_position, net.win,
        net.queue_id, net.platform_id, net.patch_version, net.tier,
        net.participant_id,
        arrayStringConcat(
            arraySort(
                arrayFlatten(
                    groupArray(
                        arrayWithConstant(
                            assumeNotNull(toUInt32(net.net_count)),
                            toString(net.effective_item_id)
                        )
                    )
                )
            ),
            ','
        ) AS start_items
    FROM (
        SELECT
            ie.match_id AS match_id,
            ie.participant_id AS participant_id,
            CASE
                WHEN ie.type = 'ITEM_PURCHASED' THEN ie.item_id
                WHEN ie.type = 'ITEM_UNDO'      THEN ie.before_id
            END AS effective_item_id,
            sum(CASE
                WHEN ie.type = 'ITEM_PURCHASED' THEN 1
                WHEN ie.type = 'ITEM_UNDO'      THEN -1
            END) AS net_count,
            any(mp.champion_id)              AS champion_id,
            any(mp.team_position)            AS team_position,
            any(mp.win)                      AS win,
            any(m.queue_id)                  AS queue_id,
            any(m.platform_id)               AS platform_id,
            any(m.patch_version)             AS patch_version,
            any(assumeNotNull(mp.tier))      AS tier
        FROM pg_item_event AS ie
        INNER JOIN pg_match_participant AS mp
            ON ie.match_id = mp.match_id AND ie.participant_id = mp.participant_id
        INNER JOIN pg_match AS m
            ON ie.match_id = m.match_id
        WHERE ie.timestamp <= 60000
          AND ie.type IN ('ITEM_PURCHASED', 'ITEM_UNDO')
          AND m.queue_id = 420
          AND mp.tier IS NOT NULL
          AND m.patch_version IS NOT NULL
          AND mp.team_position != ''
          AND ie.match_id NOT IN (SELECT DISTINCT match_id FROM match_start_item_build_local)
        GROUP BY ie.match_id, ie.participant_id,
                 CASE
                     WHEN ie.type = 'ITEM_PURCHASED' THEN ie.item_id
                     WHEN ie.type = 'ITEM_UNDO'      THEN ie.before_id
                 END
        HAVING net_count > 0
    ) AS net
    GROUP BY net.match_id, net.participant_id,
             net.champion_id, net.team_position, net.win,
             net.queue_id, net.platform_id, net.patch_version, net.tier
    HAVING start_items != ''
) AS agg;
