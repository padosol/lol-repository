-- ============================================================
-- 조회: 챔피언별 스펠 페어 통계 (라인별 + 티어 범위 합산)
-- base: metapick.lol_analytics.mv_champion_spell_stats
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- [1] 단일 라인 + 단일 티어 — 스펠 페어 Top 5
-- ─────────────────────────────────────────────────────────────
SELECT
  summoner1id,
  summoner2id,
  ARRAY[summoner1id, summoner2id] AS spell_ids,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM `metapick.lol_analytics.mv_champion_spell_stats`
WHERE patch_version_int   = 1607
  AND platform_id         = 'KR'
  AND individual_position = 'MIDDLE'
  AND champion_id         = 103
  AND tier_bucket         = 6000
ORDER BY pick_count DESC
LIMIT 5;


-- ─────────────────────────────────────────────────────────────
-- [2] 라인별 스펠 페어 Top 3 — 티어 범위 합산
-- 한 챔피언의 모든 라인 메타를 한 번에.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    summoner1id,
    summoner2id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count,
    SUM(loss_count) AS loss_count
  FROM `metapick.lol_analytics.mv_champion_spell_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000              -- DIAMOND+ (>=, BETWEEN, IN 자유 변형)
  GROUP BY individual_position, summoner1id, summoner2id
)
SELECT
  individual_position,
  summoner1id,
  summoner2id,
  ARRAY[summoner1id, summoner2id] AS spell_ids,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY individual_position
  ORDER BY pick_count DESC
) <= 3
ORDER BY individual_position, pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [3] 슬롯 무관 스펠 페어 정규화 — 티어 범위 합산
-- (4, 12) 와 (12, 4) 를 같은 페어로 취급.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    LEAST(summoner1id, summoner2id)    AS spell_lo,
    GREATEST(summoner1id, summoner2id) AS spell_hi,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_spell_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000
  GROUP BY individual_position,
           LEAST(summoner1id, summoner2id),
           GREATEST(summoner1id, summoner2id)
)
SELECT
  individual_position,
  ARRAY[spell_lo, spell_hi] AS spell_pair,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
ORDER BY individual_position, pick_count DESC;
