-- ============================================================
-- mart MV: 챔피언 × 스킬 빌드(레벨 1~9) 단위 픽/승/패 통계
--
-- 차원: patch_version_int, platform_id, tier_bucket,
--       individual_position, champion_id
-- 행 단위: skill1 ~ skill9 — 레벨 1~9 시 찍은 스킬 슬롯 (Q/W/E/R)
--          (R 는 6 레벨 이상부터 사용 가능. 9 레벨까지면 1차 max 결정 완료)
-- 측정값: pick_count, win_count, loss_count
--
-- base: metapick.lol_analytics.match_participant_fact
-- 갱신: BQ 자동 incremental refresh (30분 주기)
--
-- 설계 노트:
--   - skill1~15 전체 저장 시 카디널리티 폭증 + 후반 레벨은 메타 결정 후의 잡음.
--     1~9 가 max 우선순위(Q>W>E 등) 결정에 충분.
--   - "max 우선순위" 같은 derived 분석은 조회 시점에 UNNEST 후 빈도 집계
--     (slot 별 dominant 스킬) 로 도출 가능.
-- ============================================================
CREATE MATERIALIZED VIEW `metapick.lol_analytics.mv_champion_skill_stats` PARTITION BY RANGE_BUCKET(patch_version_int, GENERATE_ARRAY(1400, 2500, 1)) CLUSTER BY platform_id,
champion_id,
tier_bucket,
individual_position OPTIONS (
  enable_refresh = TRUE,
  refresh_interval_minutes = 30,
  description = "패치 × 플랫폼 × 티어 × 라인 × 챔피언 × 스킬빌드(1~9레벨) 픽/승/패 (자동 갱신 MV)"
) AS
SELECT patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
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
  COUNT(*) AS pick_count,
  COUNTIF(win) AS win_count,
  COUNTIF(NOT win) AS loss_count
FROM `metapick.lol_analytics.match_participant_fact`
WHERE tier_bucket IS NOT NULL -- 언랭 제외
  AND skill1 IS NOT NULL -- 최소 1레벨은 찍은 게임만
GROUP BY patch_version_int,
  patch_version,
  platform_id,
  tier_bucket,
  individual_position,
  champion_id,
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
  skill15;