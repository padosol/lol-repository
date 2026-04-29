-- ============================================================
-- mart MV: 챔피언 × 시작템 세트 단위 픽/승/패 통계
--
-- 차원: patch_version_int, platform_id, tier_bucket,
--       individual_position, champion_id
-- 행 단위: start_item_ids_json (시작 아이템 세트의 JSON 직렬화 STRING)
-- 측정값: pick_count, win_count, loss_count
--
-- base: metapick.lol_analytics.match_participant_fact
-- 갱신: BQ 자동 incremental refresh (30분 주기)
--
-- 그룹키 / 출력 트릭:
--   BQ 는 ARRAY 컬럼을 GROUP BY 에서 직접 못 받고, MV 는 ANY_VALUE/STRING_AGG
--   같은 함수도 허용하지 않음. 그래서 TO_JSON_STRING(start_item_ids) 를
--   그룹키이자 출력 컬럼으로 동시에 사용 (BQ 공식 패턴).
--
--   상류 PostgreSQL 에서 ID 오름차순으로 array_agg 되어 들어오므로
--   같은 시작 세트는 항상 동일 JSON 표현 → 그룹핑 정확성 보장.
--
--   조회 시점에 ARRAY 가 필요하면 다음으로 복원:
--     ARRAY(SELECT SAFE_CAST(x AS INT64)
--           FROM UNNEST(JSON_VALUE_ARRAY(start_item_ids_json)) x)
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_champion_start_item_stats`
PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1))
CLUSTER BY platform_id, champion_id, tier_bucket, individual_position
OPTIONS (
  enable_refresh           = TRUE,
  refresh_interval_minutes = 30,
  description              = "패치 × 플랫폼 × 티어 × 라인 × 챔피언 × 시작템 세트 픽/승/패 (자동 갱신 MV)"
)
AS
SELECT
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  TO_JSON_STRING(start_item_ids) AS start_item_ids_json,
  COUNT(*)                       AS pick_count,
  COUNTIF(win)                   AS win_count,
  COUNTIF(NOT win)               AS loss_count
FROM `metapick.lol_analytics.match_participant_fact`
WHERE tier_bucket IS NOT NULL                  -- 언랭 제외
  AND ARRAY_LENGTH(start_item_ids) > 0         -- 시작 아이템 기록이 있는 게임만
GROUP BY
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  start_item_ids_json;                         -- SELECT 의 alias 그대로 그룹키
