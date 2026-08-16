-- ============================================================
-- 조회: 챔피언별 3코어 아이템 빌드 통계 (라인별)
-- base: metapick.lol_analytics.mv_champion_item_build_stats
--
-- 행 단위: (patch, platform, tier_bucket, position, champion) × (item1, item2, item3)
-- item1/2/3 = 1코어 / 2코어 / 3코어 (구매 순서대로의 완성템 슬롯)
-- MV 정의상 3코어 모두 완성된 게임만 집계 (item1, item2, item3 NOT NULL)
--
-- 호출 패턴: 백엔드가 한 번에 모든 라인 데이터를 받고, 프론트엔드에서
--            individual_position 으로 필터링.
-- ============================================================
-- ─────────────────────────────────────────────────────────────
-- [1] 단일 티어 — 라인별 3코어 빌드 Top 10
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    item1,
    item2,
    item3,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count,
    SUM(loss_count) AS loss_count
  FROM `metapick.lol_analytics.mv_champion_item_build_stats`
  WHERE patch_version_int = 1607 -- 패치 (예: 16.07)
    AND platform_id = 'KR'
    AND champion_id = 103 -- 예: 아리
    AND tier_bucket = 6000 -- DIAMOND
  GROUP BY individual_position,
    item1,
    item2,
    item3
)
SELECT individual_position,
  item1,
  item2,
  item3,
  ARRAY [item1, item2, item3] AS item_build,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg QUALIFY ROW_NUMBER() OVER (
    PARTITION BY individual_position
    ORDER BY pick_count DESC
  ) <= 10
ORDER BY individual_position,
  pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [2] 티어 범위 합산 — 라인별 다이아 이상(6000+) 3코어 빌드 Top 10
-- MV 행이 (..., tier_bucket, ...) 단위로 이미 집계되어 있으므로
-- 범위 필터 시 GROUP BY 에서 tier_bucket 제외 + SUM 으로 재집계.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    item1,
    item2,
    item3,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count,
    SUM(loss_count) AS loss_count
  FROM `metapick.lol_analytics.mv_champion_item_build_stats`
  WHERE patch_version_int = 1605
    AND platform_id = 'KR'
    AND champion_id = 64
    AND tier_bucket >= 9000 -- DIAMOND 이상
  GROUP BY individual_position,
    item1,
    item2,
    item3
)
SELECT individual_position,
  item1,
  item2,
  item3,
  ARRAY [item1, item2, item3] AS item_build,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg QUALIFY ROW_NUMBER() OVER (
    PARTITION BY individual_position
    ORDER BY pick_count DESC
  ) <= 10
ORDER BY individual_position,
  pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [3] 패치 범위 합산 — 최근 3패치(예: 1605~1607) 라인별 3코어 빌드 Top 10
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    item1,
    item2,
    item3,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_item_build_stats`
  WHERE patch_version_int BETWEEN 1605 AND 1607
    AND platform_id = 'KR'
    AND champion_id = 103
    AND tier_bucket = 6000
  GROUP BY individual_position,
    item1,
    item2,
    item3
)
SELECT individual_position,
  item1,
  item2,
  item3,
  ARRAY [item1, item2, item3] AS item_build,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg QUALIFY ROW_NUMBER() OVER (
    PARTITION BY individual_position
    ORDER BY pick_count DESC
  ) <= 10
ORDER BY individual_position,
  pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [4] 슬롯별 개별 아이템 빈도 — 라인별 1코어 / 2코어 / 3코어 각각의 픽/승률
-- "이 챔피언이 1코어로 가장 많이 가는 아이템은?" 같은 질문용.
-- UNION ALL 로 슬롯 위치(1/2/3) 라벨 부여.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT 1 AS slot_position,
    individual_position,
    item1 AS item_id,
    pick_count,
    win_count
  FROM `metapick.lol_analytics.mv_champion_item_build_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND champion_id = 103
    AND tier_bucket = 6000
  UNION ALL
  SELECT 2 AS slot_position,
    individual_position,
    item2 AS item_id,
    pick_count,
    win_count
  FROM `metapick.lol_analytics.mv_champion_item_build_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND champion_id = 103
    AND tier_bucket = 6000
  UNION ALL
  SELECT 3 AS slot_position,
    individual_position,
    item3 AS item_id,
    pick_count,
    win_count
  FROM `metapick.lol_analytics.mv_champion_item_build_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND champion_id = 103
    AND tier_bucket = 6000
),
slot_agg AS (
  SELECT individual_position,
    slot_position,
    item_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM agg
  GROUP BY individual_position,
    slot_position,
    item_id
)
SELECT individual_position,
  slot_position,
  -- 1=1코어, 2=2코어, 3=3코어
  item_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM slot_agg QUALIFY ROW_NUMBER() OVER (
    PARTITION BY individual_position,
    slot_position
    ORDER BY pick_count DESC
  ) <= 5 -- 슬롯별 Top 5
ORDER BY individual_position,
  slot_position,
  pick_count DESC;