-- match 백필 집계 쿼리.
-- Spring Batch 청크 단위 호출: :startId (inclusive), :endId (exclusive),
-- :season, :queueIds (List -> IN (?, ?) 확장).
-- 청크 키는 match.id 기준.
--
-- 행 단위: 1 매치
-- 용도: BQ match_fact (1행/매치) → mv_match_count_stats 의 base.
--       밴률/픽률 등 게임 단위 분모가 필요한 모든 분석에서 재사용.
SELECT m.match_id,
    m.season,
    m.patch_version,
    (
        split_part(m.patch_version, '.', 1)::int * 100
            + split_part(m.patch_version, '.', 2)::int
    ) AS patch_version_int,
    m.platform_id,
    m.queue_id,
    m.tier_bucket,
    m.game_duration,
    m.game_creation,
    m.game_end_timestamp
FROM match m
WHERE m.id >= :startId
    AND m.id < :endId
    AND m.season = :season
    AND m.queue_id IN (:queueIds)
