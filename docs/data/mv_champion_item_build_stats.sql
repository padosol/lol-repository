-- ============================================================
-- mart MV: 챔피언 × 3코어 빌드 단위 픽/승/패 통계
--
-- 차원: patch_version_int, platform_id, tier_bucket,
--       individual_position, champion_id
-- 행 단위: (item1, item2, item3) 슬롯 — 1~3코어
-- 측정값: pick_count, win_count, loss_count
--
-- base: metapick.lol_analytics.match_participant_fact
-- 갱신: BQ 자동 incremental refresh (30분 주기)
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_champion_item_build_stats` PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1)) CLUSTER BY platform_id,
champion_id,
tier_bucket,
individual_position OPTIONS (
  require_partition_filter = True,
  enable_refresh = TRUE,
  refresh_interval_minutes = 30,
  description = "패치 × 플랫폼 × 티어 × 라인 × 챔피언 × 3코어 빌드 픽/승/패 (자동 갱신 MV)"
) AS
SELECT patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  item1,
  item2,
  item3,
  COUNT(*) AS pick_count,
  COUNTIF(win) AS win_count,
  COUNTIF(NOT win) AS loss_count
FROM `metapick.lol_analytics.match_participant_fact`
WHERE tier_bucket IS NOT NULL -- 언랭 제외
  AND item1 IS NOT NULL -- 3코어 모두 완성된 게임만
  AND item2 IS NOT NULL
  AND item3 IS NOT NULL
GROUP BY patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  item1,
  item2,
  item3;