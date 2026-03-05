-- =====================================================
-- 04: 조회 쿼리 모음 (참고용)
-- =====================================================
-- 이 파일은 직접 실행하지 않고, 필요한 쿼리를 복사하여 사용합니다.
-- clickhouse-client에서 파라미터 바인딩으로 실행하세요.
-- =====================================================


-- =====================================================
-- Section 1: 챔피언 통계 조회
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


-- =====================================================
-- Section 2: 챔피언 룬 통계 조회
-- =====================================================
-- 특정 챔피언의 라인별 룬 페이지 승률/픽률 조회
-- 파라미터: patch_version, platform_id, tier, champion_id, team_position
-- =====================================================

WITH
    rune_stats AS (
        SELECT
            primary_style_id, sub_style_id,
            primary_perk0, primary_perk1, primary_perk2, primary_perk3,
            sub_perk0, sub_perk1,
            stat_perk_defense, stat_perk_flex, stat_perk_offense,
            sum(games) AS games,
            sum(wins)  AS wins
        FROM champion_rune_stats_agg
        WHERE patch_version = {patch_version:String}
          AND platform_id   = {platform_id:String}
          AND tier           = {tier:String}
          AND champion_id    = {champion_id:Int32}
          AND team_position  = {team_position:String}
        GROUP BY primary_style_id, sub_style_id,
                 primary_perk0, primary_perk1, primary_perk2, primary_perk3,
                 sub_perk0, sub_perk1,
                 stat_perk_defense, stat_perk_flex, stat_perk_offense
    ),
    total AS (
        SELECT sum(games) AS total_games FROM rune_stats
    )
SELECT
    rs.primary_style_id,
    rs.sub_style_id,
    rs.primary_perk0, rs.primary_perk1, rs.primary_perk2, rs.primary_perk3,
    rs.sub_perk0, rs.sub_perk1,
    rs.stat_perk_defense, rs.stat_perk_flex, rs.stat_perk_offense,
    rs.games,
    rs.wins / rs.games       AS win_rate,
    rs.games / t.total_games AS pick_rate
FROM rune_stats AS rs
CROSS JOIN total AS t
ORDER BY rs.games DESC;


-- =====================================================
-- Section 3: 챔피언 소환사 주문 조합 조회
-- =====================================================
-- 특정 챔피언의 라인별 소환사 주문 조합 승률/픽률 조회
-- 파라미터: patch_version, platform_id, tier, champion_id, team_position
-- =====================================================

WITH
    spell_stats AS (
        SELECT
            summoner1id, summoner2id,
            sum(games) AS games,
            sum(wins)  AS wins
        FROM champion_spell_stats_agg
        WHERE patch_version = {patch_version:String}
          AND platform_id   = {platform_id:String}
          AND tier           = {tier:String}
          AND champion_id    = {champion_id:Int32}
          AND team_position  = {team_position:String}
        GROUP BY summoner1id, summoner2id
    ),
    total AS (
        SELECT sum(games) AS total_games FROM spell_stats
    )
SELECT
    ss.summoner1id,
    ss.summoner2id,
    ss.games,
    ss.wins / ss.games       AS win_rate,
    ss.games / t.total_games AS pick_rate
FROM spell_stats AS ss
CROSS JOIN total AS t
ORDER BY ss.games DESC;


-- =====================================================
-- Section 4: 챔피언 스킬빌드 통계 조회
-- =====================================================
-- 사용법: clickhouse-client 에서 파라미터 바인딩으로 실행
--   예) clickhouse-client --param_patch_version='15.1' --param_platform_id='KR' \
--       --param_tier='GOLD' --param_champion_id=236 --param_team_position='BOTTOM'
-- =====================================================

WITH
    skill_stats AS (
        SELECT
            skill_build,
            sum(games) AS games,
            sum(wins)  AS wins
        FROM champion_skill_build_stats_agg
        WHERE patch_version = {patch_version:String}
          AND platform_id   = {platform_id:String}
          AND tier           = {tier:String}
          AND champion_id    = {champion_id:Int32}
          AND team_position  = {team_position:String}
        GROUP BY skill_build
    ),
    total AS (
        SELECT sum(games) AS total_games FROM skill_stats
    )
SELECT
    sk.skill_build,
    sk.games,
    sk.wins / sk.games       AS win_rate,
    sk.games / t.total_games AS pick_rate
FROM skill_stats AS sk
CROSS JOIN total AS t
ORDER BY sk.games DESC;


-- =====================================================
-- Section 5: 챔피언 시작 아이템 빌드 통계 조회
-- =====================================================
-- 특정 챔피언의 라인별 시작 아이템 빌드 승률/픽률 조회
-- 파라미터: patch_version, platform_id, tier, champion_id, team_position
-- =====================================================

WITH
    item_stats AS (
        SELECT
            start_items,
            sum(games) AS games,
            sum(wins)  AS wins
        FROM champion_start_item_stats_agg
        WHERE patch_version = {patch_version:String}
          AND platform_id   = {platform_id:String}
          AND tier           = {tier:String}
          AND champion_id    = {champion_id:Int32}
          AND team_position  = {team_position:String}
        GROUP BY start_items
    ),
    total AS (
        SELECT sum(games) AS total_games FROM item_stats
    )
SELECT
    its.start_items,
    its.games,
    its.wins / its.games       AS win_rate,
    its.games / t.total_games  AS pick_rate
FROM item_stats AS its
CROSS JOIN total AS t
ORDER BY its.games DESC;
