-- ============================================================
-- mart MV: 챔피언 × 라인 단위 픽/승 카운트
--
-- 차원: patch_version_int, platform_id, tier_bucket, individual_position, champion_id
-- 행 단위: 차원 조합당 1행
-- 측정값: pick_count, win_count, loss_count
--
-- base: metapick.lol_analytics.match_participant_fact (1행 = 1참가자)
-- 갱신: BQ 자동 incremental refresh (30분 주기)
--
-- 다른 MV 와의 차이:
--   - mv_champion_ban_stats: individual_position 차원 없음 (밴은 챔프 셀렉트 단계, 라인 미정)
--   - mv_champion_item_build_stats: 동일한 라인 차원이지만 추가로 (item1,item2,item3) 슬롯 차원 보유
--
-- 분모(픽률 계산용 match_count):
--   이 MV 에는 없음. mv_match_count_stats 와 조인해서 산출.
--   pick_rate = pick_count / match_count
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_champion_pick_stats`
PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1))
CLUSTER BY platform_id, individual_position, champion_id, tier_bucket
OPTIONS (
  enable_refresh           = TRUE,
  refresh_interval_minutes = 30,
  description              = "패치 × 플랫폼 × 티어 × 라인 × 챔피언 픽/승/패 (자동 갱신 MV)"
)
AS
SELECT
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  COUNT(*)         AS pick_count,
  COUNTIF(win)     AS win_count,
  COUNTIF(NOT win) AS loss_count
FROM `metapick.lol_analytics.match_participant_fact`
WHERE tier_bucket         IS NOT NULL                  -- 언랭 제외
  AND individual_position IS NOT NULL                  -- 라인 미식별 제외
GROUP BY
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id;
