-- ============================================================
-- 조회: 챔피언별 신발 통계 (라인별)
-- base: metapick.lol_analytics.mv_champion_boot_stats
--
-- 행 단위: (patch, platform, tier_bucket, position, champion) × boot_id
-- boot_id = 매치/참가자별 마지막으로 구매한 신발 (최종 신발).
--
-- 호출 패턴: 백엔드가 한 번에 모든 라인 데이터를 받고, 프론트엔드에서
--            individual_position 으로 필터링.
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- [1] 단일 티어 — 라인별 신발 Top 5
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    boot_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count,
    SUM(loss_count) AS loss_count
  FROM `metapick.lol_analytics.mv_champion_boot_stats`
  WHERE patch_version_int = 1607        -- 패치 (예: 16.07)
    AND platform_id       = 'KR'
    AND champion_id       = 103          -- 예: 아리
    AND tier_bucket       = 6000         -- DIAMOND
  GROUP BY individual_position, boot_id
)
SELECT individual_position,
  boot_id,
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
-- [2] 티어 범위 합산 — 라인별 다이아 이상(6000+) 신발 Top 5
-- MV 행이 (..., tier_bucket, ...) 단위로 이미 집계되어 있으므로
-- 범위 필터 시 GROUP BY 에서 tier_bucket 제외 + SUM 으로 재집계.
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    boot_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count,
    SUM(loss_count) AS loss_count
  FROM `metapick.lol_analytics.mv_champion_boot_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000              -- DIAMOND 이상
  GROUP BY individual_position, boot_id
)
SELECT individual_position,
  boot_id,
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
-- [3] 패치 범위 합산 — 최근 3패치(예: 1605~1607) 라인별 신발 Top 5
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    boot_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_boot_stats`
  WHERE patch_version_int BETWEEN 1605 AND 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       = 6000
  GROUP BY individual_position, boot_id
)
SELECT individual_position,
  boot_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
QUALIFY ROW_NUMBER() OVER (
  PARTITION BY individual_position
  ORDER BY pick_count DESC
) <= 5
ORDER BY individual_position, pick_count DESC;


-- ─────────────────────────────────────────────────────────────
-- [4] 라인별 전체 신발 분포 (Top 제한 없이 모든 boot_id)
-- "이 챔피언의 라인별 신발 메타 전체 비율 보기" 용.
-- 표본 30 미만은 노이즈로 컷 (필요시 임계값 조정).
-- ─────────────────────────────────────────────────────────────
WITH agg AS (
  SELECT individual_position,
    boot_id,
    SUM(pick_count) AS pick_count,
    SUM(win_count)  AS win_count
  FROM `metapick.lol_analytics.mv_champion_boot_stats`
  WHERE patch_version_int = 1607
    AND platform_id       = 'KR'
    AND champion_id       = 103
    AND tier_bucket       >= 6000
  GROUP BY individual_position, boot_id
)
SELECT individual_position,
  boot_id,
  pick_count,
  win_count,
  SAFE_DIVIDE(win_count, pick_count) AS win_rate
FROM agg
WHERE pick_count >= 30
ORDER BY individual_position, pick_count DESC;
