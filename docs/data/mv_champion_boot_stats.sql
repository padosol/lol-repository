-- ============================================================
-- mart MV: 챔피언 × 신발 단위 픽/승/패 통계
--
-- 차원: patch_version_int, platform_id, tier_bucket,
--       individual_position, champion_id
-- 행 단위: boot_id (단일 스칼라)
-- 측정값: pick_count, win_count, loss_count
--
-- base: metapick.lol_analytics.match_participant_fact
-- 갱신: BQ 자동 incremental refresh (30분 주기)
--
-- boot_id 정의:
--   상류에서 매치/참가자별 마지막 구매 신발(`ITEM_PURCHASED` 중 latest).
--   LoL 의 신발 슬롯은 1개라 "최종 신발 = 사용한 신발" 의미가 자연스러움.
--   T1 → T2 업그레이드 시 T2 가 보고됨.
--
-- 슬롯 직접 그룹키 — start_item_ids 같은 ARRAY 우회 트릭 불필요.
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_champion_boot_stats`
PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1))
CLUSTER BY platform_id, champion_id, tier_bucket, individual_position
OPTIONS (
  enable_refresh           = TRUE,
  refresh_interval_minutes = 30,
  description              = "패치 × 플랫폼 × 티어 × 라인 × 챔피언 × 신발 픽/승/패 (자동 갱신 MV)"
)
AS
SELECT
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  boot_id,
  COUNT(*)         AS pick_count,
  COUNTIF(win)     AS win_count,
  COUNTIF(NOT win) AS loss_count
FROM `metapick.lol_analytics.match_participant_fact`
WHERE tier_bucket IS NOT NULL                  -- 언랭 제외
  AND boot_id     IS NOT NULL                  -- 신발 미구매 게임 제외
GROUP BY
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  boot_id;
