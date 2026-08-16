-- ============================================================
-- 조회: 라인별 챔피언 픽/승/픽률/밴률/승률
-- base: metapick.lol_analytics.mv_champion_pick_stats
--       metapick.lol_analytics.mv_champion_ban_stats
--       metapick.lol_analytics.mv_match_count_stats
--
-- 행 단위: (patch, platform, tier_bucket, individual_position, champion) × pick_count/win_count
--
-- 정의:
--   - pick_rate = pick_count / match_count   — 라인별 다름
--   - ban_rate  = ban_count  / match_count   — 라인 무관 (챔프 5라인 행 모두 같은 값)
--   - win_rate  = win_count  / pick_count    — 라인별 다름
--
-- 다른 챔피언 통계 쿼리와 차이:
--   - 픽/승은 individual_position 별로 분리되지만 밴은 챔프 단위로 합산해서 broadcasting
--   - 분모(match_count) 는 patch×platform×tier 단일값이므로 CROSS JOIN
-- ============================================================
-- ─────────────────────────────────────────────────────────────
-- [1] 단일 티어 — 라인별 챔피언 픽 Top 30 (라인당 상위)
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats`
  WHERE patch_version_int = 1607 -- 패치 (예: 16.07)
    AND platform_id = 'KR'
    AND tier_bucket = 6000 -- DIAMOND
  GROUP BY individual_position,
    champion_id
)
SELECT individual_position,
  champion_id,
  pick_count,
  win_count
FROM agg QUALIFY ROW_NUMBER() OVER (
    PARTITION BY individual_position
    ORDER BY pick_count DESC
  ) <= 30
ORDER BY individual_position,
  pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [2] 티어 범위 합산 — 다이아 이상(6000+) 라인별 챔피언 픽 Top 10
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket >= 6000 -- DIAMOND 이상
  GROUP BY individual_position,
    champion_id
)
SELECT individual_position,
  champion_id,
  pick_count,
  win_count
FROM agg QUALIFY ROW_NUMBER() OVER (
    PARTITION BY individual_position
    ORDER BY pick_count DESC
  ) <= 10
ORDER BY individual_position,
  pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [3] 패치 범위 합산 — 최근 3패치(예: 1605~1607) 라인별 챔피언 픽 Top 10
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats`
  WHERE patch_version_int BETWEEN 1605 AND 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
  GROUP BY individual_position,
    champion_id
)
SELECT individual_position,
  champion_id,
  pick_count,
  win_count
FROM agg QUALIFY ROW_NUMBER() OVER (
    PARTITION BY individual_position
    ORDER BY pick_count DESC
  ) <= 10
ORDER BY individual_position,
  pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [4] 픽률 — 단일 티어 — 라인별 Top 30
-- pick_rate = pick_count / match_count
-- 분모는 mv_match_count_stats 의 단일 행과 CROSS JOIN.
-- ─────────────────────────────────────────────────────────────
WITH picks AS (
  SELECT individual_position,
    champion_id,
    SUM(pick_count) AS pick_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
  GROUP BY individual_position,
    champion_id
),
matches AS (
  SELECT match_count
  FROM `metapick.lol_analytics.mv_match_count_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
)
SELECT p.individual_position,
  p.champion_id,
  p.pick_count,
  m.match_count,
  SAFE_DIVIDE(p.pick_count, m.match_count) AS pick_rate
FROM picks p
  CROSS JOIN matches m QUALIFY ROW_NUMBER() OVER (
    PARTITION BY p.individual_position
    ORDER BY p.pick_count DESC
  ) <= 30
ORDER BY individual_position,
  pick_rate DESC;
-- ─────────────────────────────────────────────────────────────
-- [5] 승률 — 단일 티어 — 라인별 Top 30 (최소 픽 100 이상 필터)
-- win_rate = win_count / pick_count
-- 픽수가 적으면 분산 커서 신뢰도 떨어짐 → 최소 픽 컷 적용.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
  GROUP BY individual_position,
    champion_id
)
SELECT individual_position,
  champion_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
WHERE pick_count >= 100 QUALIFY ROW_NUMBER() OVER (
    PARTITION BY individual_position
    ORDER BY SAFE_DIVIDE(win_count, pick_count) DESC
  ) <= 30
ORDER BY individual_position,
  win_rate DESC;
-- ─────────────────────────────────────────────────────────────
-- [6] 픽률 + 밴률 + 승률 통합 — 라인별 챔피언 종합 통계
-- ban_rate 는 라인 무관 챔프 단위 → 5라인 행 모두 동일값으로 broadcasting.
-- ─────────────────────────────────────────────────────────────
WITH denom AS (
  SELECT match_count
  FROM `metapick.lol_analytics.mv_match_count_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
),
ban_per_champ AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
  GROUP BY champion_id
),
pick_per_lane AS (
  SELECT individual_position,
    champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
  GROUP BY individual_position,
    champion_id
)
SELECT l.individual_position,
  l.champion_id,
  l.pick_count,
  l.win_count,
  COALESCE(b.ban_count, 0) AS ban_count,
  d.match_count,
  SAFE_DIVIDE(l.pick_count, d.match_count) AS pick_rate,
  SAFE_DIVIDE(COALESCE(b.ban_count, 0), d.match_count) AS ban_rate,
  SAFE_DIVIDE(l.win_count, l.pick_count) AS win_rate
FROM pick_per_lane l
  LEFT JOIN ban_per_champ b USING (champion_id)
  CROSS JOIN denom d
ORDER BY l.individual_position,
  l.pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [7] 픽률 + 밴률 + 승률 — 티어 범위 합산 (다이아 이상)
-- ─────────────────────────────────────────────────────────────
WITH denom AS (
  SELECT SUM(match_count) AS match_count
  FROM `metapick.lol_analytics.mv_match_count_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket >= 6000
),
ban_per_champ AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket >= 6000
  GROUP BY champion_id
),
pick_per_lane AS (
  SELECT individual_position,
    champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket >= 6000
  GROUP BY individual_position,
    champion_id
)
SELECT l.individual_position,
  l.champion_id,
  l.pick_count,
  l.win_count,
  COALESCE(b.ban_count, 0) AS ban_count,
  d.match_count,
  SAFE_DIVIDE(l.pick_count, d.match_count) AS pick_rate,
  SAFE_DIVIDE(COALESCE(b.ban_count, 0), d.match_count) AS ban_rate,
  SAFE_DIVIDE(l.win_count, l.pick_count) AS win_rate
FROM pick_per_lane l
  LEFT JOIN ban_per_champ b USING (champion_id)
  CROSS JOIN denom d
ORDER BY l.individual_position,
  l.pick_count DESC;
-- ─────────────────────────────────────────────────────────────
-- [8] 특정 라인 — 픽률/밴률/승률 통합 Top 30
-- 예: MIDDLE 라인.
-- ─────────────────────────────────────────────────────────────
WITH denom AS (
  SELECT match_count
  FROM `metapick.lol_analytics.mv_match_count_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
),
ban_per_champ AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
  GROUP BY champion_id
),
pick_lane AS (
  SELECT champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
    AND individual_position = 'MIDDLE' -- 라인 고정
  GROUP BY champion_id
)
SELECT l.champion_id,
  l.pick_count,
  l.win_count,
  COALESCE(b.ban_count, 0) AS ban_count,
  d.match_count,
  SAFE_DIVIDE(l.pick_count, d.match_count) AS pick_rate,
  SAFE_DIVIDE(COALESCE(b.ban_count, 0), d.match_count) AS ban_rate,
  SAFE_DIVIDE(l.win_count, l.pick_count) AS win_rate
FROM pick_lane l
  LEFT JOIN ban_per_champ b USING (champion_id)
  CROSS JOIN denom d
ORDER BY pick_rate DESC
LIMIT 30;
-- ─────────────────────────────────────────────────────────────
-- [9] 챔피언 티어 산출 — (patch, platform, tier_bucket, lane) 단일 segment
--
-- 점수 모델:
--   wilson_wr   = 95% Wilson score lower bound — 픽수 적은 챔프 자동 강등
--   presence    = pick_rate + ban_rate          — 라인 내 메타 영향력
--   tier_score  = 0.6 × percentile(wilson_wr) + 0.4 × percentile(presence)
--                 — 라인별 (PARTITION BY individual_position) percentile 산출
--
-- 티어 컷 (percentile of tier_score):
--   S+ ≥ 0.97, S ≥ 0.90, A ≥ 0.75, B ≥ 0.50, C ≥ 0.20, D < 0.20
--
-- 픽수 컷:
--   pick_count >= 20  — Wilson 이 보정해도 노이즈 방지용 최소 표본
--
-- tier_bucket >= 6000 — DIAMOND 이상 합산 segment.
--   denom 은 여러 티어 행을 SUM, ban/pick 은 GROUP BY 로 합산.
--
-- 주의: BQ MV 는 window function 미지원이라 이 query 는 MV 화 불가.
--       자주 쓰면 일반 VIEW 또는 scheduled query 로 승격.
-- ─────────────────────────────────────────────────────────────
WITH denom AS (
  SELECT SUM(match_count) AS match_count
  FROM `metapick.lol_analytics.mv_match_count_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket >= 6000
),
ban_per_champ AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket >= 6000
  GROUP BY champion_id
),
pick_per_lane AS (
  SELECT individual_position,
    champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats` d
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket >= 6000
  GROUP BY individual_position,
    champion_id
),
base AS (
  SELECT l.individual_position,
    l.champion_id,
    l.pick_count,
    l.win_count,
    COALESCE(b.ban_count, 0) AS ban_count,
    d.match_count,
    SAFE_DIVIDE(l.pick_count, d.match_count) AS pick_rate,
    SAFE_DIVIDE(COALESCE(b.ban_count, 0), d.match_count) AS ban_rate,
    SAFE_DIVIDE(
      l.pick_count + COALESCE(b.ban_count, 0),
      d.match_count
    ) AS presence,
    SAFE_DIVIDE(l.win_count, l.pick_count) AS win_rate,
    -- Wilson score 95% lower bound (z = 1.96)
    SAFE_DIVIDE(
      (l.win_count + 1.96 * 1.96 / 2) / l.pick_count - 1.96 * SQRT(
        SAFE_DIVIDE(
          l.win_count * (l.pick_count - l.win_count),
          l.pick_count
        ) + 1.96 * 1.96 / 4
      ) / l.pick_count,
      1 + 1.96 * 1.96 / l.pick_count
    ) AS wilson_wr
  FROM pick_per_lane l
    LEFT JOIN ban_per_champ b USING (champion_id)
    CROSS JOIN denom d
  WHERE l.pick_count >= 20
),
ranked AS (
  SELECT *,
    PERCENT_RANK() OVER (
      PARTITION BY individual_position
      ORDER BY wilson_wr
    ) AS pct_wilson_wr,
    PERCENT_RANK() OVER (
      PARTITION BY individual_position
      ORDER BY presence
    ) AS pct_presence
  FROM base
)
SELECT individual_position,
  champion_id,
  pick_count,
  win_count,
  ban_count,
  match_count,
  ROUND(pick_rate * 100, 2) AS pick_rate_pct,
  ROUND(ban_rate * 100, 2) AS ban_rate_pct,
  ROUND(win_rate * 100, 2) AS win_rate_pct,
  ROUND(wilson_wr * 100, 2) AS wilson_wr_pct,
  ROUND(presence * 100, 2) AS presence_pct,
  ROUND(0.6 * pct_wilson_wr + 0.4 * pct_presence, 4) AS tier_score,
  CASE
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.97 THEN 'S+'
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.90 THEN 'S'
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.75 THEN 'A'
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.50 THEN 'B'
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.20 THEN 'C'
    ELSE 'D'
  END AS tier
FROM ranked
ORDER BY individual_position,
  tier_score DESC;
-- ─────────────────────────────────────────────────────────────
-- [10] 챔피언 티어 — 특정 라인만 (예: MIDDLE)
-- 출력 줄 수가 적어 결과 검토 / op.gg 비교용으로 사용.
-- ─────────────────────────────────────────────────────────────
WITH denom AS (
  SELECT match_count
  FROM `metapick.lol_analytics.mv_match_count_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
),
ban_per_champ AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
  GROUP BY champion_id
),
pick_lane AS (
  SELECT champion_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count) AS win_count
  FROM `metapick.lol_analytics.mv_champion_pick_stats`
  WHERE patch_version_int = 1607
    AND platform_id = 'KR'
    AND tier_bucket = 6000
    AND individual_position = 'MIDDLE' -- 라인 고정
  GROUP BY champion_id
),
base AS (
  SELECT l.champion_id,
    l.pick_count,
    l.win_count,
    COALESCE(b.ban_count, 0) AS ban_count,
    d.match_count,
    SAFE_DIVIDE(l.pick_count, d.match_count) AS pick_rate,
    SAFE_DIVIDE(COALESCE(b.ban_count, 0), d.match_count) AS ban_rate,
    SAFE_DIVIDE(
      l.pick_count + COALESCE(b.ban_count, 0),
      d.match_count
    ) AS presence,
    SAFE_DIVIDE(l.win_count, l.pick_count) AS win_rate,
    SAFE_DIVIDE(
      (l.win_count + 1.96 * 1.96 / 2) / l.pick_count - 1.96 * SQRT(
        SAFE_DIVIDE(
          l.win_count * (l.pick_count - l.win_count),
          l.pick_count
        ) + 1.96 * 1.96 / 4
      ) / l.pick_count,
      1 + 1.96 * 1.96 / l.pick_count
    ) AS wilson_wr
  FROM pick_lane l
    LEFT JOIN ban_per_champ b USING (champion_id)
    CROSS JOIN denom d
  WHERE l.pick_count >= 20
),
ranked AS (
  SELECT *,
    PERCENT_RANK() OVER (
      ORDER BY wilson_wr
    ) AS pct_wilson_wr,
    PERCENT_RANK() OVER (
      ORDER BY presence
    ) AS pct_presence
  FROM base
)
SELECT champion_id,
  pick_count,
  win_count,
  ban_count,
  ROUND(pick_rate * 100, 2) AS pick_rate_pct,
  ROUND(ban_rate * 100, 2) AS ban_rate_pct,
  ROUND(win_rate * 100, 2) AS win_rate_pct,
  ROUND(wilson_wr * 100, 2) AS wilson_wr_pct,
  ROUND(presence * 100, 2) AS presence_pct,
  ROUND(0.6 * pct_wilson_wr + 0.4 * pct_presence, 4) AS tier_score,
  CASE
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.97 THEN 'S+'
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.90 THEN 'S'
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.75 THEN 'A'
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.50 THEN 'B'
    WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.20 THEN 'C'
    ELSE 'D'
  END AS tier
FROM ranked
ORDER BY tier_score DESC;