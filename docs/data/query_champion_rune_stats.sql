-- ============================================================
-- 조회: 챔피언별 룬 페이지 통계
-- base: metapick.lol_analytics.mv_champion_rune_stats
--
-- 행 단위: (patch, platform, tier_bucket, position, champion)
--          × 룬 페이지 (메인 트리/키스톤/메인 보조 3개/서브 트리/서브 룬 2개)
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- [1] 단일 티어 — 풀 룬 페이지 Top 10
-- ─────────────────────────────────────────────────────────────
SELECT
  primary_style_id,
  primary_perk0,
  primary_perk1,
  primary_perk2,
  primary_perk3,
  sub_style_id,
  sub_perk0,
  sub_perk1,
  ARRAY[primary_perk0, primary_perk1, primary_perk2, primary_perk3] AS primary_perks,
  ARRAY[sub_perk0, sub_perk1]                                       AS secondary_perks,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM `metapick.lol_analytics.mv_champion_rune_stats`
WHERE patch_version_int   = 1607
  AND platform_id         = 'KR'
  AND individual_position = 'MIDDLE'
  AND champion_id         = 103          -- 예: 아리
  AND tier_bucket         = 6000         -- DIAMOND
ORDER BY pick_count DESC
LIMIT 10;


-- ─────────────────────────────────────────────────────────────
-- [2] 키스톤(primary_perk0)별 픽/승률 — 가장 자주 쓰는 단일 차원
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    primary_perk0,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_rune_stats`
  WHERE patch_version_int   = 1607
    AND platform_id         = 'KR'
    AND individual_position = 'MIDDLE'
    AND champion_id         = 103
    AND tier_bucket         = 6000
  GROUP BY primary_perk0
)
SELECT
  primary_perk0 AS keystone_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
ORDER BY pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [3] 트리 조합 (메인 × 서브) 픽/승률
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    primary_style_id,
    sub_style_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_rune_stats`
  WHERE patch_version_int   = 1607
    AND platform_id         = 'KR'
    AND individual_position = 'MIDDLE'
    AND champion_id         = 103
    AND tier_bucket         = 6000
  GROUP BY primary_style_id, sub_style_id
)
SELECT
  primary_style_id,
  sub_style_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
ORDER BY pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [4] 특정 키스톤 사용자 대상 — 풀 룬 페이지 분포
-- 예: "감전(8112)을 고른 아리 유저들의 룬 페이지 Top 10"
-- ─────────────────────────────────────────────────────────────
SELECT
  primary_style_id,
  primary_perk0,
  primary_perk1,
  primary_perk2,
  primary_perk3,
  sub_style_id,
  sub_perk0,
  sub_perk1,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM `metapick.lol_analytics.mv_champion_rune_stats`
WHERE patch_version_int   = 1607
  AND platform_id         = 'KR'
  AND individual_position = 'MIDDLE'
  AND champion_id         = 103
  AND tier_bucket         = 6000
  AND primary_perk0       = 8112         -- 키스톤 ID 지정 (예: 감전)
ORDER BY pick_count DESC
LIMIT 10;


-- ─────────────────────────────────────────────────────────────
-- [5] 티어 범위 합산 — 다이아 이상(6000+)에서 키스톤 분포
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    primary_perk0,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_rune_stats`
  WHERE patch_version_int   = 1607
    AND platform_id         = 'KR'
    AND individual_position = 'MIDDLE'
    AND champion_id         = 103
    AND tier_bucket         >= 6000
  GROUP BY primary_perk0
)
SELECT
  primary_perk0 AS keystone_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
ORDER BY pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [6] 개별 룬 빈도 — 보조 슬롯 포함 모든 룬을 풀어 단일 룬 단위로
-- "이 챔피언이 어떤 룬을 가장 많이 찍는가" (슬롯 무관 합계)
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    perk_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_rune_stats`,
    UNNEST([primary_perk0, primary_perk1, primary_perk2, primary_perk3,
            sub_perk0,     sub_perk1]) AS perk_id
  WHERE patch_version_int   = 1607
    AND platform_id         = 'KR'
    AND individual_position = 'MIDDLE'
    AND champion_id         = 103
    AND tier_bucket         = 6000
    AND perk_id IS NOT NULL
  GROUP BY perk_id
)
SELECT
  perk_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
ORDER BY pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [7] 라인별 룬 페이지 Top 5 — 티어 범위 합산
-- individual_position 을 GROUP BY 키로, tier_bucket 은 범위 합산.
-- 한 챔피언의 모든 라인 메타를 한 번에 보고 싶을 때.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    primary_style_id,
    primary_perk0,
    primary_perk1,
    primary_perk2,
    primary_perk3,
    sub_style_id,
    sub_perk0,
    sub_perk1,
    SUM(pick_count)  AS pick_count,
    SUM(win_count)   AS win_count,
    SUM(loss_count)  AS loss_count
  FROM `metapick.lol_analytics.mv_champion_rune_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000          -- DIAMOND+ (>=, BETWEEN, IN 모두 가능)
  GROUP BY
    individual_position,
    primary_style_id, primary_perk0, primary_perk1, primary_perk2, primary_perk3,
    sub_style_id, sub_perk0, sub_perk1
)
SELECT
  individual_position,
  primary_style_id,
  primary_perk0,
  primary_perk1,
  primary_perk2,
  primary_perk3,
  sub_style_id,
  sub_perk0,
  sub_perk1,
  ARRAY[primary_perk0, primary_perk1, primary_perk2, primary_perk3] AS primary_perks,
  ARRAY[sub_perk0, sub_perk1]                                       AS secondary_perks,
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
-- [8] 라인별 키스톤 Top 3 — 티어 범위 합산 (간략 버전)
-- 풀 룬페이지 대신 primary_perk0(키스톤) 만 그룹키.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    primary_perk0,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_rune_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000
  GROUP BY individual_position, primary_perk0
)
SELECT
  individual_position,
  primary_perk0 AS keystone_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY individual_position
  ORDER BY pick_count DESC
) <= 3
ORDER BY individual_position, pick_count DESC;
