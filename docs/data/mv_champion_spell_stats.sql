-- ============================================================
-- mart MV: 챔피언 × 스펠 페어 단위 픽/승/패 통계
--
-- 차원: patch_version_int, platform_id, tier_bucket,
--       individual_position, champion_id
-- 행 단위: (summoner1id, summoner2id) — D/F 슬롯 그대로 보존
-- 측정값: pick_count, win_count, loss_count
--
-- base: metapick.lol_analytics.match_participant_fact
-- 갱신: BQ 자동 incremental refresh (30분 주기)
--
-- 참고: D/F 슬롯 무관한 페어 분석이 필요하면 조회 시점에
--       LEAST/GREATEST 로 정규화하거나 별도 정규화 컬럼 추가.
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_champion_spell_stats`
PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1))
CLUSTER BY platform_id, champion_id, tier_bucket, individual_position
OPTIONS (
  enable_refresh           = TRUE,
  refresh_interval_minutes = 30,
  description              = "패치 × 플랫폼 × 티어 × 라인 × 챔피언 × 스펠 페어 픽/승/패 (자동 갱신 MV)"
)
AS
SELECT
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  summoner1id,
  summoner2id,
  COUNT(*)         AS pick_count,
  COUNTIF(win)     AS win_count,
  COUNTIF(NOT win) AS loss_count
FROM `metapick.lol_analytics.match_participant_fact`
WHERE tier_bucket IS NOT NULL                  -- 언랭 제외
  AND summoner1id IS NOT NULL
  AND summoner2id IS NOT NULL
GROUP BY
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  summoner1id,
  summoner2id;
