-- ============================================================
-- mart MV: 챔피언 × 밴 단위 카운트
--
-- 차원: patch_version_int, platform_id, tier_bucket, champion_id, pick_turn
-- 행 단위: 차원 조합당 1행
-- 측정값: ban_count
--
-- base: metapick.lol_analytics.match_ban_fact (1행 = 1밴)
-- 갱신: BQ 자동 incremental refresh (30분 주기)
--
-- 다른 MV 와의 차이:
--   - individual_position 차원 없음 (밴은 챔프 셀렉트 단계, 라인 미정)
--   - matchup/win 컬럼 없음 (게임 시작 전이라 의미 없음)
--   - pick_turn 추가 차원 (밴 순서 분포 분석용 — 1밴/2밴이 위협도 더 높음)
--
-- 분모(밴률 계산용 match_count):
--   이 MV 에는 없음. 별도 mv_match_count_stats(추후 작성) 와 조인해서 산출.
--   ban_rate = ban_count / match_count
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_champion_ban_stats`
PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1))
CLUSTER BY platform_id, champion_id, tier_bucket
OPTIONS (
  enable_refresh           = TRUE,
  refresh_interval_minutes = 30,
  description              = "패치 × 플랫폼 × 티어 × 챔피언 × 밴순서 밴 카운트 (자동 갱신 MV)"
)
AS
SELECT
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  champion_id,
  pick_turn,
  COUNT(*) AS ban_count
FROM `metapick.lol_analytics.match_ban_fact`
WHERE tier_bucket IS NOT NULL                  -- 언랭 제외
  AND champion_id <> -1                        -- 밴 슬롯 미사용 (이중 안전장치)
GROUP BY
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  champion_id,
  pick_turn;
