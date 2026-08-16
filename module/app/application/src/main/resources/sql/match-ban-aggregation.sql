-- match_ban 백필 집계 쿼리.
-- Spring Batch 청크 단위 호출: :startId (inclusive), :endId (exclusive),
-- :season, :queueIds (List -> IN (?, ?) 확장).
-- 청크 키는 match_ban.id 기준.
--
-- 행 단위: 1 ban (정상 게임 기준 10행/매치 = 5밴 × 2팀)
-- match_ban.champion_id = -1 → 밴 슬롯 사용 안 함, 집계에서 제외.
SELECT mb.match_id,
    m.season,
    m.patch_version,
    (
        split_part(m.patch_version, '.', 1)::int * 100
            + split_part(m.patch_version, '.', 2)::int
    ) AS patch_version_int,
    m.platform_id,
    m.queue_id,
    m.tier_bucket,
    mb.team_id,
    mb.pick_turn,
    mb.champion_id
FROM match_ban mb
    JOIN match m ON mb.match_id = m.match_id
WHERE mb.id >= :startId
    AND mb.id < :endId
    AND m.season = :season
    AND m.queue_id IN (:queueIds)
    AND mb.champion_id <> -1                     -- 밴 슬롯 미사용 제외
