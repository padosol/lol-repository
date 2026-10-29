-- 통계 적재용 티어 버킷. average_tier 에서 파생되며 챌린저 이상(10000~)은 10000 하나로 묶는다.
ALTER TABLE match ADD COLUMN IF NOT EXISTS tier_bucket INTEGER
    GENERATED ALWAYS AS (CASE WHEN average_tier >= 10000 THEN 10000 ELSE (average_tier / 1000) * 1000 END) STORED;
