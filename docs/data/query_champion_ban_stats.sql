-- ============================================================
-- 조회: 챔피언별 밴 통계
-- base: metapick.lol_analytics.mv_champion_ban_stats
--
-- 행 단위: (patch, platform, tier_bucket, champion, pick_turn) × ban_count
--
-- 다른 챔피언 통계 쿼리와 차이:
--   - individual_position 그룹핑 없음 (밴에는 라인 차원 없음)
--   - 밴률(ban_rate) = ban_count / match_count.
--     분모는 mv_match_count_stats (별도 MV) 와 조인해서 산출.
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- [1] 단일 티어 — 챔피언별 총 밴 수 Top 30
-- pick_turn 합산해서 챔프 단위로 봄.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607        -- 패치 (예: 16.07)
    AND platform_id       = 'KR'
    AND tier_bucket       = 6000        -- DIAMOND
  GROUP BY champion_id
)
SELECT champion_id,
  ban_count
FROM agg
ORDER BY ban_count DESC
LIMIT 30;


-- ─────────────────────────────────────────────────────────────
-- [2] 티어 범위 합산 — 다이아 이상(6000+) 챔피언별 총 밴 수 Top 30
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND tier_bucket       >= 6000        -- DIAMOND 이상
  GROUP BY champion_id
)
SELECT champion_id,
  ban_count
FROM agg
ORDER BY ban_count DESC
LIMIT 30;


-- ─────────────────────────────────────────────────────────────
-- [3] 패치 범위 합산 — 최근 3패치(예: 1605~1607) 챔피언별 총 밴 수 Top 30
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int BETWEEN 1605 AND 1607
    AND platform_id       = 'KR'
    AND tier_bucket       = 6000
  GROUP BY champion_id
)
SELECT champion_id,
  ban_count
FROM agg
ORDER BY ban_count DESC
LIMIT 30;


-- ─────────────────────────────────────────────────────────────
-- [4] pick_turn 별 밴 분포 — "1밴(가장 위협적)으로 가장 많이 밴되는 챔프" 같은 분석용
-- 결과: 챔프 × pick_turn 조합. 한 챔프가 여러 pick_turn 에 걸쳐 분포될 수 있음.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT champion_id,
    pick_turn,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND tier_bucket       >= 6000
  GROUP BY champion_id, pick_turn
)
SELECT champion_id,
  pick_turn,
  ban_count
FROM agg
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY pick_turn
  ORDER BY ban_count DESC
) <= 10                                   -- pick_turn 별 Top 10
ORDER BY pick_turn, ban_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [5] 특정 챔피언의 pick_turn 분포 — "이 챔프는 주로 몇번째에 밴되나?"
-- ─────────────────────────────────────────────────────────────
SELECT pick_turn,
  SUM(ban_count) AS ban_count
FROM `metapick.lol_analytics.mv_champion_ban_stats`
WHERE patch_version_int = 1607
  AND platform_id       = 'KR'
  AND tier_bucket       >= 6000
  AND champion_id       = 157           -- 예: 야스오
GROUP BY pick_turn
ORDER BY pick_turn;


-- ─────────────────────────────────────────────────────────────
-- [6] 밴률 — 단일 티어 — Top 30 챔프
-- ban_rate = SUM(ban_count) / match_count
-- 두 MV 모두 같은 (patch, platform, tier) 단일 행으로 좁혀진 뒤 CROSS JOIN.
-- ─────────────────────────────────────────────────────────────
WITH bans AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND tier_bucket       = 6000
  GROUP BY champion_id
),
matches AS (
  SELECT match_count
  FROM `metapick.lol_analytics.mv_match_count_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND tier_bucket       = 6000
)
SELECT b.champion_id,
  b.ban_count,
  m.match_count,
  SAFE_DIVIDE(b.ban_count, m.match_count) AS ban_rate
FROM bans b
CROSS JOIN matches m
ORDER BY ban_rate DESC
LIMIT 30;


-- ─────────────────────────────────────────────────────────────
-- [7] 밴률 — 티어 범위 합산 (다이아 이상)
-- 두 MV 모두 같은 필터로 SUM 한 뒤 비율 산출.
-- ─────────────────────────────────────────────────────────────
WITH bans AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND tier_bucket       >= 6000
  GROUP BY champion_id
),
matches AS (
  SELECT SUM(match_count) AS match_count
  FROM `metapick.lol_analytics.mv_match_count_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND tier_bucket       >= 6000
)
SELECT b.champion_id,
  b.ban_count,
  m.match_count,
  SAFE_DIVIDE(b.ban_count, m.match_count) AS ban_rate
FROM bans b
CROSS JOIN matches m
ORDER BY ban_rate DESC
LIMIT 30;


-- ─────────────────────────────────────────────────────────────
-- [8] 밴률 — 패치 범위 합산 (최근 3패치)
-- ─────────────────────────────────────────────────────────────
WITH bans AS (
  SELECT champion_id,
    SUM(ban_count) AS ban_count
  FROM `metapick.lol_analytics.mv_champion_ban_stats`
  WHERE patch_version_int BETWEEN 1605 AND 1607
    AND platform_id       = 'KR'
    AND tier_bucket       = 6000
  GROUP BY champion_id
),
matches AS (
  SELECT SUM(match_count) AS match_count
  FROM `metapick.lol_analytics.mv_match_count_stats`
  WHERE patch_version_int BETWEEN 1605 AND 1607
    AND platform_id       = 'KR'
    AND tier_bucket       = 6000
)
SELECT b.champion_id,
  b.ban_count,
  m.match_count,
  SAFE_DIVIDE(b.ban_count, m.match_count) AS ban_rate
FROM bans b
CROSS JOIN matches m
ORDER BY ban_rate DESC
LIMIT 30;
