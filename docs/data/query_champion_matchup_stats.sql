-- ============================================================
-- 조회: 챔피언별 라인 매치업(상대 챔피언) 전적 (라인별 + 티어 범위)
-- base: metapick.lol_analytics.mv_champion_matchup_stats
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- [1] 단일 라인 + 단일 티어 — 매치업 전적 Top 10
-- ─────────────────────────────────────────────────────────────
SELECT
  matchup_champion_id,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM `metapick.lol_analytics.mv_champion_matchup_stats`
WHERE patch_version_int   = 1607
  AND platform_id         = 'KR'
  AND individual_position = 'MIDDLE'
  AND champion_id         = 103          -- 우리 챔피언 (예: 아리)
  AND tier_bucket         = 6000
ORDER BY pick_count DESC
LIMIT 10;


-- ─────────────────────────────────────────────────────────────
-- [2] 라인별 매치업 Top 5 — 티어 범위 합산 (가장 자주 만나는 상대)
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    matchup_champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count,
    SUM(loss_count) AS loss_count
  FROM `metapick.lol_analytics.mv_champion_matchup_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000          -- DIAMOND+
  GROUP BY individual_position, matchup_champion_id
)
SELECT
  individual_position,
  matchup_champion_id,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY individual_position
  ORDER BY pick_count DESC
) <= 5
ORDER BY individual_position, pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [3] 라인별 카운터 픽 — 승률 낮은 상대 Top 5 (최소 표본 100건 이상)
-- "이 챔프가 약한 상대" 를 찾을 때.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    matchup_champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_matchup_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000
  GROUP BY individual_position, matchup_champion_id
)
SELECT
  individual_position,
  matchup_champion_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
WHERE pick_count >= 100                        -- 표본 너무 적은 매치업 노이즈 제외
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY individual_position
  ORDER BY SAFE_DIVIDE(win_count, pick_count) ASC, pick_count DESC
) <= 5
ORDER BY individual_position, win_rate ASC;


-- ─────────────────────────────────────────────────────────────
-- [4] 라인별 카운터 — 승률 높은 상대 Top 5 ("이 챔프가 잘 잡는 상대")
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    matchup_champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_matchup_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000
  GROUP BY individual_position, matchup_champion_id
)
SELECT
  individual_position,
  matchup_champion_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
WHERE pick_count >= 100
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY individual_position
  ORDER BY SAFE_DIVIDE(win_count, pick_count) DESC, pick_count DESC
) <= 5
ORDER BY individual_position, win_rate DESC;
