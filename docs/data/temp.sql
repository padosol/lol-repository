EXPLAIN (ANALYZE, BUFFERS) WITH mp_batch AS MATERIALIZED (
    SELECT mp.match_id,
        m.season,
        m.patch_version,
        (
            split_part(m.patch_version, '.', 1)::int * 100 + split_part(m.patch_version, '.', 2)::int
        ) AS patch_version_int,
        m.platform_id,
        m.queue_id,
        m.game_duration,
        m.tier_bucket,
        mp.match_participant_id,
        mp.participant_id,
        mp.team_id,
        mp.individual_position,
        mp.puuid,
        mp.champion_id,
        mp.champion_name,
        mp.win,
        mp.kills,
        mp.deaths,
        mp.assists,
        mp.summoner1id,
        mp.summoner2id,
        mp.primary_style_id,
        mp.primary_perk0,
        mp.primary_perk1,
        mp.primary_perk2,
        mp.primary_perk3,
        mp.sub_style_id,
        mp.sub_perk0,
        mp.sub_perk1,
        mpu.champion_id AS matchup_champion_id,
        mpu.champion_name AS matchup_champion_name
    FROM match_participant mp
        JOIN match_participant mpu ON mp.match_id = mpu.match_id
        AND mp.individual_position = mpu.individual_position
        AND mp.team_id != mpu.team_id
        JOIN match m ON mp.match_id = m.match_id
    WHERE mp.match_participant_id >= 1416523
        AND mp.match_participant_id < 1430000
        AND m.season = 16
        AND m.queue_id IN (420, 440)
),
target_matches AS MATERIALIZED (
    SELECT DISTINCT match_id
    FROM mp_batch
),
completed_items AS (
    SELECT item_id
    FROM item_meta
    WHERE season = 16
        AND active = true
        AND type = 'LEGENDARY' -- ← 완성템 구분 값 직접 채우기
),
boot_items AS (
    SELECT item_id
    FROM item_meta
    WHERE season = 16
        AND active = true
        AND type = 'BOOTS'
),
boot_purchases AS (
    -- 매치/참가자별 마지막으로 구매한 신발 (T1 → T2 업그레이드 시 T2 가 최종)
    -- LoL 은 신발 슬롯 1개라 마지막 구매가 곧 "사용한 신발"
    SELECT DISTINCT ON (ie.match_id, ie.participant_id)
        ie.match_id,
        ie.participant_id,
        ie.item_id AS boot_id
    FROM item_event ie
    WHERE ie.match_id = ANY(
            ARRAY(
                SELECT match_id
                FROM target_matches
            )
        )
        AND ie.type = 'ITEM_PURCHASED'
        AND ie.item_id IN (
            SELECT item_id
            FROM boot_items
        )
    ORDER BY ie.match_id,
        ie.participant_id,
        ie.timestamp DESC,
        ie.event_index DESC
),
events AS MATERIALIZED (
    SELECT ie.match_id,
        ie.participant_id,
        ie.timestamp AS event_ts_ms,
        ie.event_index AS event_id,
        ie.type AS event_type,
        ie.item_id AS item_id,
        NULL::int AS skill_slot
    FROM item_event ie
    WHERE ie.match_id = ANY(
            ARRAY(
                SELECT match_id
                FROM target_matches
            )
        )
        AND ie.type = 'ITEM_PURCHASED'
        AND ie.item_id IN (
            SELECT item_id
            FROM completed_items
        )
    UNION ALL
    SELECT se.match_id,
        se.participant_id,
        se.timestamp AS event_ts_ms,
        se.event_index AS event_id,
        'SKILL_LEVEL_UP'::text AS event_type,
        NULL::int AS item_id,
        se.skill_slot AS skill_slot
    FROM skill_level_up_event se
    WHERE se.match_id = ANY(
            ARRAY(
                SELECT match_id
                FROM target_matches
            )
        )
),
start_item_net AS (
    SELECT ie.match_id,
        ie.participant_id,
        CASE
            WHEN ie.type = 'ITEM_PURCHASED' THEN ie.item_id
            WHEN ie.type = 'ITEM_UNDO' THEN ie.before_id
        END AS effective_item_id,
        SUM(
            CASE
                WHEN ie.type = 'ITEM_PURCHASED' THEN 1
                WHEN ie.type = 'ITEM_UNDO' THEN -1
            END
        ) AS net_count
    FROM item_event ie
    WHERE ie.match_id = ANY(
            ARRAY(
                SELECT match_id
                FROM target_matches
            )
        )
        AND ie.timestamp <= 60000
        AND ie.type IN ('ITEM_PURCHASED', 'ITEM_UNDO')
        AND NOT (
            ie.type = 'ITEM_PURCHASED'
            AND ie.item_id IN (
                SELECT item_id
                FROM starting_item_exclusion
                WHERE season = 16
                    AND active = true
            )
        )
    GROUP BY ie.match_id,
        ie.participant_id,
        CASE
            WHEN ie.type = 'ITEM_PURCHASED' THEN ie.item_id
            WHEN ie.type = 'ITEM_UNDO' THEN ie.before_id
        END
),
start_items AS (
    -- 시작 아이템 세트는 "어떤 세트를 들었는가" 가 분석 본질. 구매 순서는 부가적.
    -- ID 오름차순으로 정규화해서 [1054, 2003] 으로 일관 → BQ MV 의
    -- TO_JSON_STRING 그룹키 결과도 결정성 보장 → 같은 세트가 같은 행으로 그룹핑.
    SELECT sin.match_id,
        sin.participant_id,
        array_agg(
            sin.effective_item_id
            ORDER BY sin.effective_item_id
        ) AS start_item_ids
    FROM start_item_net sin,
        LATERAL generate_series(1, sin.net_count) AS gs(n)
    WHERE sin.net_count > 0
    GROUP BY sin.match_id,
        sin.participant_id
),
-- ★ 참가자별 집계 (완성템 전체 배열 + 스킬 + challenges 조인)
agg AS (
    SELECT mp.match_id,
        mp.season,
        mp.patch_version,
        mp.patch_version_int,
        mp.platform_id,
        mp.queue_id,
        mp.game_duration,
        mp.tier_bucket,
        mp.puuid,
        mp.participant_id,
        mp.team_id,
        mp.champion_id,
        mp.champion_name,
        mp.individual_position,
        mp.win,
        mp.kills,
        mp.deaths,
        mp.assists,
        mp.summoner1id,
        mp.summoner2id,
        mp.matchup_champion_id,
        mp.matchup_champion_name,
        mp.primary_style_id,
        mp.primary_perk0,
        mp.primary_perk1,
        mp.primary_perk2,
        mp.primary_perk3,
        mp.sub_style_id,
        mp.sub_perk0,
        mp.sub_perk1,
        mpc.jungle_cs_before10minutes AS jungle_cs_10m,
        mpc.lane_minions_first10minutes AS lane_cs_10m,
        mpc.gold_per_minute,
        COALESCE(si.start_item_ids, ARRAY []::int []) AS start_item_ids,
        bp.boot_id AS boot_id,
        array_agg(
            e.item_id
            ORDER BY e.event_ts_ms,
                e.event_id
        ) FILTER (
            WHERE e.event_type = 'ITEM_PURCHASED'
        ) AS full_items,
        array_agg(
            e.skill_slot
            ORDER BY e.event_ts_ms,
                e.event_id
        ) FILTER (
            WHERE e.event_type = 'SKILL_LEVEL_UP'
        ) AS full_skills,
        -- 첫 완성템(= 1코어) 구매 타임스탬프 (ms)
        MIN(e.event_ts_ms) FILTER (
            WHERE e.event_type = 'ITEM_PURCHASED'
        ) AS first_core_ts_ms
    FROM mp_batch mp
        LEFT JOIN start_items si ON si.match_id = mp.match_id
        AND si.participant_id = mp.participant_id
        LEFT JOIN boot_purchases bp ON bp.match_id = mp.match_id
        AND bp.participant_id = mp.participant_id
        LEFT JOIN match_participant_challenges mpc ON mpc.match_id = mp.match_id
        AND mpc.puuid = mp.puuid
        JOIN events e ON e.match_id = mp.match_id
        AND e.participant_id = mp.participant_id
    GROUP BY mp.match_id,
        mp.season,
        mp.patch_version,
        mp.patch_version_int,
        mp.platform_id,
        mp.queue_id,
        mp.game_duration,
        mp.tier_bucket,
        mp.puuid,
        mp.participant_id,
        mp.team_id,
        mp.champion_id,
        mp.champion_name,
        mp.individual_position,
        mp.win,
        mp.kills,
        mp.deaths,
        mp.assists,
        mp.summoner1id,
        mp.summoner2id,
        mp.matchup_champion_id,
        mp.matchup_champion_name,
        mp.primary_style_id,
        mp.primary_perk0,
        mp.primary_perk1,
        mp.primary_perk2,
        mp.primary_perk3,
        mp.sub_style_id,
        mp.sub_perk0,
        mp.sub_perk1,
        mpc.jungle_cs_before10minutes,
        mpc.lane_minions_first10minutes,
        mpc.gold_per_minute,
        si.start_item_ids,
        bp.boot_id
)
SELECT match_id,
    season,
    patch_version,
    patch_version_int,
    platform_id,
    queue_id,
    game_duration,
    tier_bucket,
    puuid,
    participant_id,
    team_id,
    champion_id,
    champion_name,
    individual_position,
    win,
    kills,
    deaths,
    assists,
    matchup_champion_id,
    matchup_champion_name,
    -- 스펠
    summoner1id,
    summoner2id,
    ARRAY [summoner1id, summoner2id] AS spell_ids,
    -- 룬
    primary_style_id,
    primary_perk0,
    primary_perk1,
    primary_perk2,
    primary_perk3,
    ARRAY [primary_perk0, primary_perk1, primary_perk2, primary_perk3] AS primary_perks,
    sub_style_id,
    sub_perk0,
    sub_perk1,
    ARRAY [sub_perk0, sub_perk1] AS secondary_perks,
    -- 초반 CS / 분당 골드
    jungle_cs_10m,
    lane_cs_10m,
    gold_per_minute,
    -- 첫 코어 완성 타임 (ms)
    first_core_ts_ms,
    start_item_ids,
    boot_id,
    -- 3코어 배열 (최대 길이 3, NULL 패딩 없음)
    full_items [1:3] AS item_ids,
    -- 개별 슬롯 (없으면 NULL → BigQuery 집계에서 자연 제외)
    full_items [1] AS item1,
    full_items [2] AS item2,
    full_items [3] AS item3,
    full_items [4] AS item4,
    full_items [5] AS item5,
    full_items [6] AS item6,
    -- 스킬: 15레벨 배열 + 슬롯 15개 (짧으면 짧은 대로, 패딩 없음)
    full_skills [1:15] AS skill_slots,
    full_skills [1] AS skill1,
    full_skills [2] AS skill2,
    full_skills [3] AS skill3,
    full_skills [4] AS skill4,
    full_skills [5] AS skill5,
    full_skills [6] AS skill6,
    full_skills [7] AS skill7,
    full_skills [8] AS skill8,
    full_skills [9] AS skill9,
    full_skills [10] AS skill10,
    full_skills [11] AS skill11,
    full_skills [12] AS skill12,
    full_skills [13] AS skill13,
    full_skills [14] AS skill14,
    full_skills [15] AS skill15
FROM agg;