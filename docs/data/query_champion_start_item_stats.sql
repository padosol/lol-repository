-- ============================================================
-- 조회: 챔피언별 시작 아이템 세트 통계 (라인별)
-- base: metapick.lol_analytics.mv_champion_start_item_stats
--
-- 행 단위: (patch, platform, tier_bucket, position, champion) × start_item_ids_json
-- start_item_ids_json: TO_JSON_STRING(start_item_ids) 결과 (ID 오름차순 직렬화).
--                     예: "[1054,2003]"
-- ARRAY<INT64> 가 필요하면:
--   ARRAY(SELECT SAFE_CAST(x AS INT64)
--         FROM UNNEST(JSON_VALUE_ARRAY(start_item_ids_json)) x)
--
-- 호출 패턴: 백엔드가 한 번에 모든 라인 데이터를 받고, 프론트엔드에서
--            individual_position 으로 필터링.
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- [1] 단일 티어 — 라인별 시작템 세트 Top 10
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    start_item_ids_json,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count,
    SUM(loss_count) AS loss_count
  FROM `metapick.lol_analytics.mv_champion_start_item_stats`
  WHERE patch_version_int = 1607        -- 패치 (예: 16.07)
    AND platform_id       = 'KR'
    AND champion_id       = 103          -- 예: 아리
    AND tier_bucket       = 6000         -- DIAMOND
  GROUP BY individual_position, start_item_ids_json
)
SELECT
  individual_position,
  start_item_ids_json,
  ARRAY(SELECT SAFE_CAST(x AS INT64)
        FROM UNNEST(JSON_VALUE_ARRAY(start_item_ids_json)) x) AS start_item_ids,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY individual_position
  ORDER BY pick_count DESC
) <= 10
ORDER BY individual_position, pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [2] 티어 범위 합산 — 라인별 다이아 이상(6000+) 시작템 세트 Top 10
-- MV 행이 (..., tier_bucket, ...) 단위로 이미 집계되어 있으므로
-- 범위 필터 시 GROUP BY 에서 tier_bucket 제외 + SUM 으로 재집계.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    start_item_ids_json,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count,
    SUM(loss_count) AS loss_count
  FROM `metapick.lol_analytics.mv_champion_start_item_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000              -- DIAMOND 이상
  GROUP BY individual_position, start_item_ids_json
)
SELECT
  individual_position,
  start_item_ids_json,
  ARRAY(SELECT SAFE_CAST(x AS INT64)
        FROM UNNEST(JSON_VALUE_ARRAY(start_item_ids_json)) x) AS start_item_ids,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY individual_position
  ORDER BY pick_count DESC
) <= 10
ORDER BY individual_position, pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [3] 패치 범위 합산 — 최근 3패치(예: 1605~1607) 라인별 시작템 세트 Top 10
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    start_item_ids_json,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_start_item_stats`
  WHERE patch_version_int BETWEEN 1605 AND 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       = 6000
  GROUP BY individual_position, start_item_ids_json
)
SELECT
  individual_position,
  start_item_ids_json,
  ARRAY(SELECT SAFE_CAST(x AS INT64)
        FROM UNNEST(JSON_VALUE_ARRAY(start_item_ids_json)) x) AS start_item_ids,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY individual_position
  ORDER BY pick_count DESC
) <= 10
ORDER BY individual_position, pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [4] 개별 시작 아이템 빈도 — 라인별, JSON 배열을 풀어 아이템 단위로 집계
-- "시작 빌드에 부패 물약(2033) 을 넣은 게임 비율" 같은 질문용
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    SAFE_CAST(item_str AS INT64) AS item_id,
    SUM(pick_count)              AS pick_count,
    SUM(win_count)               AS win_count
  FROM `metapick.lol_analytics.mv_champion_start_item_stats`,
    UNNEST(JSON_VALUE_ARRAY(start_item_ids_json)) AS item_str
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       = 6000
  GROUP BY individual_position, item_id
)
SELECT
  individual_position,
  item_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
ORDER BY individual_position, pick_count DESC;
