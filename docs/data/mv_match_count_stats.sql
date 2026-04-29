-- ============================================================
-- mart MV: 매치 카운트 (분모 전용)
--
-- 차원: patch_version_int, platform_id, tier_bucket
-- 행 단위: 차원 조합당 1행
-- 측정값: match_count
--
-- base: metapick.lol_analytics.match_fact (1행 = 1매치)
-- 갱신: BQ 자동 incremental refresh (30분 주기)
--
-- 용도: 분모로 쓰이는 "(patch, platform, tier) 세그먼트의 총 매치 수".
--   - ban_rate    = ban_count / match_count    (mv_champion_ban_stats 와 조합)
--   - pick_rate   = pick_count / match_count   (mv_champion_pick_stats 등과 조합, 추후)
--   - 평균 게임 길이, 큐별 분포 등 게임 단위 메트릭 일반에 재사용
--
-- queue_id 차원 미포함 이유:
--   현재 backfill 이 :queueIds 로 큐를 사전 필터함 (랭크 솔로/자유 등 한정).
--   필요시 GROUP BY 에 queue_id 추가 — 카디널리티 작음.
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_match_count_stats`
PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1))
CLUSTER BY platform_id, tier_bucket
OPTIONS (
  enable_refresh           = TRUE,
  refresh_interval_minutes = 30,
  description              = "패치 × 플랫폼 × 티어 매치 카운트 (분모 전용 자동 갱신 MV)"
)
AS
SELECT
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  COUNT(*) AS match_count
FROM `metapick.lol_analytics.match_fact`
WHERE tier_bucket IS NOT NULL                  -- 언랭 제외
GROUP BY
  patch_version_int,
  patch_version,
  platform_id,
  tier_bucket;
