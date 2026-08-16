-- ============================================================
-- mart MV: 챔피언 × 상대 챔피언(라인 직접 매치업) 단위 픽/승/패 통계
--
-- 차원: patch_version_int, platform_id, tier_bucket,
--       individual_position, champion_id
-- 행 단위: matchup_champion_id (같은 individual_position 의 적팀 챔피언)
-- 측정값: pick_count, win_count, loss_count
--
-- base: metapick.lol_analytics.match_participant_fact
-- 갱신: BQ 자동 incremental refresh (30분 주기)
--
-- 참고: matchup_champion_id 는 상류 SQL 의 self-join
--       (mp.individual_position = mpu.individual_position
--        AND mp.team_id != mpu.team_id) 으로 산출됨.
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_champion_matchup_stats`
PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1))
CLUSTER BY platform_id, champion_id, tier_bucket, individual_position
OPTIONS (
  enable_refresh           = TRUE,
  refresh_interval_minutes = 30,
  description              = "패치 × 플랫폼 × 티어 × 라인 × 챔피언 × 상대 챔피언 픽/승/패 (자동 갱신 MV)"
)
AS
SELECT
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  matchup_champion_id,
  COUNT(*)         AS pick_count,
  COUNTIF(win)     AS win_count,
  COUNTIF(NOT win) AS loss_count
FROM `metapick.lol_analytics.match_participant_fact`
WHERE tier_bucket         IS NOT NULL          -- 언랭 제외
  AND matchup_champion_id IS NOT NULL          -- 동일 라인 적이 매칭된 게임만
GROUP BY
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
  matchup_champion_id;
