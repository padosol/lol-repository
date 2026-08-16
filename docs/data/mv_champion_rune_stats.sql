-- ============================================================
-- mart MV: 챔피언 × 룬 페이지 단위 픽/승/패 통계
--
-- 차원: patch_version_int, platform_id, tier_bucket,
--       individual_position, champion_id
-- 행 단위: 룬 페이지 = 메인 트리(primary_style_id) + 키스톤(primary_perk0)
--                    + 메인 보조 3개(primary_perk1~3)
--                    + 서브 트리(sub_style_id) + 서브 룬 2개(sub_perk0~1)
-- 측정값: pick_count, win_count, loss_count
--
-- base: metapick.lol_analytics.match_participant_fact
-- 갱신: BQ 자동 incremental refresh (30분 주기)
--
-- 8 스칼라 컬럼이라 ARRAY 변환 트릭 불필요 — 직접 GROUP BY.
-- 재집계 패턴 (옵션 A):
--   - 키스톤별: GROUP BY primary_perk0, SUM(pick_count)
--   - 트리 조합별: GROUP BY primary_style_id, sub_style_id, SUM(pick_count)
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_champion_rune_stats`
PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1))
CLUSTER BY platform_id, champion_id, tier_bucket, individual_position
OPTIONS (
  enable_refresh           = TRUE,
  refresh_interval_minutes = 30,
  description              = "패치 × 플랫폼 × 티어 × 라인 × 챔피언 × 룬 페이지 픽/승/패 (자동 갱신 MV)"
)
AS
SELECT
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  primary_style_id,
  primary_perk0,
  primary_perk1,
  primary_perk2,
  primary_perk3,
  sub_style_id,
  sub_perk0,
  sub_perk1,
  COUNT(*)         AS pick_count,
  COUNTIF(win)     AS win_count,
  COUNTIF(NOT win) AS loss_count
FROM `metapick.lol_analytics.match_participant_fact`
WHERE tier_bucket      IS NOT NULL             -- 언랭 제외
  AND primary_style_id IS NOT NULL             -- 메인 트리 필수
  AND primary_perk0    IS NOT NULL             -- 키스톤 필수 (룬 페이지 정의의 핵심)
  AND sub_style_id     IS NOT NULL             -- 서브 트리 필수
GROUP BY
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  primary_style_id,
  primary_perk0,
  primary_perk1,
  primary_perk2,
  primary_perk3,
  sub_style_id,
  sub_perk0,
  sub_perk1;
