-- =====================================================
-- 05: 챔피언 통계 조회 쿼리
-- =====================================================
-- 티어, 패치 버전, 플랫폼으로 라인별 챔피언 승률/밴률/픽률 조회
-- 파라미터: patch_version, platform_id, tier
-- =====================================================

WITH
    -- 챔피언별 라인별 게임수/승수
    stats AS (
        SELECT
            champion_id,
            team_position,
            sum(games) AS games,
            sum(wins)  AS wins
        FROM champion_stats_agg
        WHERE patch_version = '16.4'
          AND platform_id   = 'KR'
          AND tier           = 'CHALLENGER'
        GROUP BY champion_id, team_position
    ),
    -- 라인별 총 참가자 수 (픽률 분모)
    pick_total AS (
        SELECT
            team_position,
            sum(participant_rows) AS participant_rows
        FROM match_count_agg
        WHERE patch_version = '16.4'
          AND platform_id   = 'KR'
          AND tier           = 'CHALLENGER'
        GROUP BY team_position
    ),
    -- 챔피언별 밴 수
    ban_stats AS (
        SELECT
            champion_id,
            sum(bans) AS bans
        FROM champion_bans_agg
        WHERE patch_version = '16.4'
          AND platform_id   = 'KR'
          AND tier           = 'CHALLENGER'
        GROUP BY champion_id
    ),
    -- 전체 참가자 수 (밴률 분모)
    ban_total AS (
        SELECT sum(participant_rows) AS total_participants
        FROM match_count_agg
        WHERE patch_version = '16.4'
          AND platform_id   = 'KR'
          AND tier           = 'CHALLENGER'
    )
SELECT
    s.team_position,
    s.champion_id,
    s.wins / s.games                            AS win_rate,
    s.games / pt.participant_rows               AS pick_rate,
    coalesce(b.bans, 0) / bt.total_participants AS ban_rate
FROM stats AS s
INNER JOIN pick_total AS pt ON s.team_position = pt.team_position
LEFT  JOIN ban_stats  AS b  ON s.champion_id   = b.champion_id
CROSS JOIN ban_total  AS bt
ORDER BY s.team_position, s.games DESC;
