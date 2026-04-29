-- ============================================================
-- 조회: 챔피언별 스킬 빌드 통계 (라인별 + 티어 범위)
-- base: metapick.lol_analytics.mv_champion_skill_stats
--
-- skill1~skill9 = 레벨 1~9 시 찍은 스킬 슬롯 (Q=1, W=2, E=3, R=4)
-- ============================================================
-- ─────────────────────────────────────────────────────────────
-- [1] 단일 라인 + 단일 티어 — 스킬 빌드 시퀀스 Top 5
-- ─────────────────────────────────────────────────────────────
SELECT skill1,
  skill2,
  skill3,
  skill4,
  skill5,
  skill6,
  skill7,
  skill8,
  skill9,
  ARRAY [skill1, skill2, skill3, skill4, skill5,
        skill6, skill7, skill8, skill9] AS skill_sequence,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM `metapick.lol_analytics.mv_champion_skill_stats`
WHERE patch_version_int = 1607
  AND platform_id = 'KR'
  AND individual_position = 'MIDDLE'
  AND champion_id = 103
  AND tier_bucket = 6000
ORDER BY pick_count DESC
LIMIT 5;
-- ─────────────────────────────────────────────────────────────
-- [2] 라인별 스킬 빌드 Top 3 — 티어 범위 합산
-- 한 챔피언의 모든 라인 스킬 메타를 한 번에.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    skill1,
    skill2,
    skill3,
    skill4,
    skill5,
    skill6,
    skill7,
    skill8,
    skill9,
    skill10,
    skill11,
    skill12,
    skill13,
    skill14,
    skill15,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count,
    SUM(loss_count) AS loss_count
  FROM `metapick.lol_analytics.mv_champion_skill_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND champion_id = 103
    AND tier_bucket >= 6000
  GROUP BY individual_position,
    skill1,
    skill2,
    skill3,
    skill4,
    skill5,
    skill6,
    skill7,
    skill8,
    skill9,
    skill10,
    skill11,
    skill12,
    skill13,
    skill14,
    skill15
)
SELECT individual_position,
  skill1,
  skill2,
  skill3,
  skill4,
  skill5,
  skill6,
  skill7,
  skill8,
  skill9,
  skill10,
  skill11,
  skill12,
  skill13,
  skill14,
  skill15,
  pick_count,
  win_count,
  loss_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg QUALIFY ROW_NUMBER() OVER (
    PARTITION BY individual_position
    ORDER BY pick_count DESC
  ) <= 3
ORDER BY individual_position,
  pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [3] 라인별 1레벨 스킬 분포 — 가장 많이 찍는 1레벨 스킬
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    skill1,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_skill_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000
  GROUP BY individual_position, skill1
)
SELECT
  individual_position,
  skill1 AS first_skill,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
ORDER BY individual_position, pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [4] 라인별 max 우선순위 — 1~5 레벨 슬롯에서 Q/W/E 빈도
-- ult(R=4) 가 강제되는 6레벨 이전 = max 우선순위 결정 구간.
-- 빈도 1위 스킬이 max 1순위, 2위가 2순위 (해석은 분석가 몫).
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT
    individual_position,
    skill_id,
    SUM(pick_count) AS slot_count
  FROM `metapick.lol_analytics.mv_champion_skill_stats`,
    UNNEST([skill1, skill2, skill3, skill4, skill5]) AS skill_id
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000
    AND skill_id IS NOT NULL
    AND skill_id != 4                            -- R(=4) 제외 (1~5레벨엔 안 나옴이지만 안전)
  GROUP BY individual_position, skill_id
)
SELECT
  individual_position,
  skill_id,                                      -- 1=Q, 2=W, 3=E
  slot_count
FROM agg
ORDER BY individual_position, slot_count DESC;