package com.mmrtr.lol.infra.bigquery.adapter;

/**
 * 챔피언 통계 BigQuery 쿼리. lol-server {@code ChampionStatsBigQuerySqls} 와 같다 — 응답 동일성을 위해 고치지 않는다.
 * {@code %s} 는 {@code `dataset.table`}, 파라미터는 {@code @patch}(major × 100 + minor)·{@code @platform}·
 * {@code @tierBuckets}(Tier 점수)·{@code @championId}·{@code @position}.
 */
final class ChampionStatsBigQuerySqls {

    static final String WIN_RATES = """
            SELECT individual_position                                       AS team_position,
                   CAST(SUM(pick_count) AS INT64)                            AS total_games,
                   CAST(SUM(win_count) AS INT64)                             AS total_wins,
                   COALESCE(ROUND(SAFE_DIVIDE(SUM(win_count), SUM(pick_count)), 4), 0) AS total_win_rate
            FROM %s
            WHERE champion_id = @championId
              AND patch_version_int = @patch
              AND platform_id = @platform
              AND tier_bucket IN UNNEST(@tierBuckets)
            GROUP BY individual_position
            HAVING SUM(pick_count) > 20
            ORDER BY total_games DESC
            """;

    static final String MATCHUPS = """
            WITH
                agg AS (
                    SELECT matchup_champion_id,
                           SUM(pick_count) AS pick_count,
                           SUM(win_count)  AS win_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                      AND champion_id = @championId
                      AND individual_position = @position
                    GROUP BY matchup_champion_id
                    HAVING pick_count >= 30
                ),
                total AS (SELECT SUM(pick_count) AS total_games FROM agg)
            SELECT 'TOP'                                  AS rank_type,
                   a.matchup_champion_id                  AS opponent_champion_id,
                   a.pick_count                           AS games,
                   SAFE_DIVIDE(a.win_count, a.pick_count) AS win_rate,
                   SAFE_DIVIDE(a.pick_count, t.total_games) AS pick_rate
            FROM agg AS a
            CROSS JOIN total AS t
            QUALIFY ROW_NUMBER() OVER (
                ORDER BY SAFE_DIVIDE(a.win_count, a.pick_count) DESC, a.pick_count DESC
            ) <= 5
            UNION ALL
            SELECT 'BOTTOM'                               AS rank_type,
                   a.matchup_champion_id                  AS opponent_champion_id,
                   a.pick_count                           AS games,
                   SAFE_DIVIDE(a.win_count, a.pick_count) AS win_rate,
                   SAFE_DIVIDE(a.pick_count, t.total_games) AS pick_rate
            FROM agg AS a
            CROSS JOIN total AS t
            QUALIFY ROW_NUMBER() OVER (
                ORDER BY SAFE_DIVIDE(a.win_count, a.pick_count) ASC, a.pick_count DESC
            ) <= 5
            ORDER BY rank_type, win_rate DESC
            """;

    static final String RUNE_BUILDS = """
            WITH
                agg AS (
                    SELECT primary_style_id, sub_style_id,
                           primary_perk0, primary_perk1, primary_perk2, primary_perk3,
                           sub_perk0, sub_perk1,
                           SUM(pick_count) AS pick_count,
                           SUM(win_count)  AS win_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                      AND champion_id = @championId
                      AND individual_position = @position
                    GROUP BY primary_style_id, sub_style_id,
                             primary_perk0, primary_perk1, primary_perk2, primary_perk3,
                             sub_perk0, sub_perk1
                ),
                total AS (SELECT SUM(pick_count) AS total_games FROM agg)
            SELECT a.primary_style_id, a.sub_style_id,
                   a.primary_perk0, a.primary_perk1, a.primary_perk2, a.primary_perk3,
                   a.sub_perk0, a.sub_perk1,
                   a.pick_count                           AS games,
                   SAFE_DIVIDE(a.win_count, a.pick_count) AS win_rate,
                   SAFE_DIVIDE(a.pick_count, t.total_games) AS pick_rate
            FROM agg AS a
            CROSS JOIN total AS t
            ORDER BY a.pick_count DESC
            LIMIT 5
            """;

    static final String SPELL_STATS = """
            WITH
                agg AS (
                    SELECT summoner1id, summoner2id,
                           SUM(pick_count) AS pick_count,
                           SUM(win_count)  AS win_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                      AND champion_id = @championId
                      AND individual_position = @position
                    GROUP BY summoner1id, summoner2id
                    HAVING pick_count >= 30
                ),
                total AS (SELECT SUM(pick_count) AS total_games FROM agg)
            SELECT a.summoner1id, a.summoner2id,
                   a.pick_count                           AS games,
                   SAFE_DIVIDE(a.win_count, a.pick_count) AS win_rate,
                   SAFE_DIVIDE(a.pick_count, t.total_games) AS pick_rate
            FROM agg AS a
            CROSS JOIN total AS t
            ORDER BY a.pick_count DESC
            LIMIT 3
            """;

    static final String SKILL_BUILDS = """
            WITH
                agg AS (
                    SELECT skill1, skill2, skill3, skill4, skill5,
                           skill6, skill7, skill8, skill9, skill10,
                           skill11, skill12, skill13, skill14, skill15,
                           SUM(pick_count) AS pick_count,
                           SUM(win_count)  AS win_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                      AND champion_id = @championId
                      AND individual_position = @position
                    GROUP BY skill1, skill2, skill3, skill4, skill5,
                             skill6, skill7, skill8, skill9, skill10,
                             skill11, skill12, skill13, skill14, skill15
                ),
                total AS (SELECT SUM(pick_count) AS total_games FROM agg)
            SELECT TO_JSON_STRING([
                       a.skill1, a.skill2, a.skill3, a.skill4, a.skill5,
                       a.skill6, a.skill7, a.skill8, a.skill9, a.skill10,
                       a.skill11, a.skill12, a.skill13, a.skill14, a.skill15
                   ])                                       AS skill_build,
                   a.pick_count                             AS games,
                   SAFE_DIVIDE(a.win_count, a.pick_count)   AS win_rate,
                   SAFE_DIVIDE(a.pick_count, t.total_games) AS pick_rate
            FROM agg AS a
            CROSS JOIN total AS t
            ORDER BY a.pick_count DESC
            LIMIT 3
            """;

    static final String START_ITEM_BUILDS = """
            WITH
                agg AS (
                    SELECT start_item_ids_json,
                           SUM(pick_count) AS pick_count,
                           SUM(win_count)  AS win_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                      AND champion_id = @championId
                      AND individual_position = @position
                    GROUP BY start_item_ids_json
                    HAVING pick_count >= 30
                ),
                total AS (SELECT SUM(pick_count) AS total_games FROM agg)
            SELECT a.start_item_ids_json                  AS start_items,
                   a.pick_count                           AS games,
                   SAFE_DIVIDE(a.win_count, a.pick_count) AS win_rate,
                   SAFE_DIVIDE(a.pick_count, t.total_games) AS pick_rate
            FROM agg AS a
            CROSS JOIN total AS t
            ORDER BY a.pick_count DESC
            LIMIT 5
            """;

    static final String BOOT_BUILDS = """
            WITH
                agg AS (
                    SELECT boot_id,
                           SUM(pick_count) AS pick_count,
                           SUM(win_count)  AS win_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                      AND champion_id = @championId
                      AND individual_position = @position
                    GROUP BY boot_id
                    HAVING pick_count >= 30
                ),
                total AS (SELECT SUM(pick_count) AS total_games FROM agg)
            SELECT a.boot_id,
                   a.pick_count                           AS games,
                   SAFE_DIVIDE(a.win_count, a.pick_count) AS win_rate,
                   SAFE_DIVIDE(a.pick_count, t.total_games) AS pick_rate
            FROM agg AS a
            CROSS JOIN total AS t
            ORDER BY a.pick_count DESC
            LIMIT 5
            """;

    static final String ITEM_BUILDS = """
            WITH
                agg AS (
                    SELECT item1, item2, item3,
                           SUM(pick_count) AS pick_count,
                           SUM(win_count)  AS win_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                      AND champion_id = @championId
                      AND individual_position = @position
                    GROUP BY item1, item2, item3
                    HAVING pick_count >= 30
                ),
                total AS (SELECT SUM(pick_count) AS total_games FROM agg)
            SELECT TO_JSON_STRING([a.item1, a.item2, a.item3]) AS item_build,
                   a.pick_count                           AS games,
                   SAFE_DIVIDE(a.win_count, a.pick_count) AS win_rate,
                   SAFE_DIVIDE(a.pick_count, t.total_games) AS pick_rate
            FROM agg AS a
            CROSS JOIN total AS t
            ORDER BY a.pick_count DESC
            LIMIT 5
            """;

    static final String AVG_STATS = """
            SELECT individual_position                                        AS team_position,
                   AVG(kills)                                                 AS avg_kills,
                   AVG(deaths)                                                AS avg_deaths,
                   AVG(assists)                                               AS avg_assists,
                   SAFE_DIVIDE(SUM(kills) + SUM(assists),
                               GREATEST(SUM(deaths), 1))                      AS kda,
                   AVG(gold_per_minute)                                       AS avg_gold_per_minute,
                   AVG(lane_cs_10m)                                           AS avg_lane_cs_10m,
                   AVG(jungle_cs_10m)                                         AS avg_jungle_cs_10m
            FROM %s
            WHERE patch_version_int = @patch
              AND platform_id = @platform
              AND tier_bucket IN UNNEST(@tierBuckets)
              AND champion_id = @championId
              AND individual_position IS NOT NULL
            GROUP BY individual_position
            HAVING COUNT(*) > 20
            """;

    static final String STATS_BY_POSITION = """
            WITH
                denom AS (
                    SELECT SUM(match_count) AS match_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                ),
                ban_per_champ AS (
                    SELECT champion_id,
                           SUM(ban_count) AS ban_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                    GROUP BY champion_id
                ),
                pick_per_lane AS (
                    SELECT individual_position,
                           champion_id,
                           SUM(pick_count) AS pick_count,
                           SUM(win_count)  AS win_count
                    FROM %s
                    WHERE patch_version_int = @patch
                      AND platform_id = @platform
                      AND tier_bucket IN UNNEST(@tierBuckets)
                    GROUP BY individual_position, champion_id
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
                           SAFE_DIVIDE(
                               (l.win_count + 1.96 * 1.96 / 2) / l.pick_count - 1.96 * SQRT(
                                   SAFE_DIVIDE(
                                       l.win_count * (l.pick_count - l.win_count),
                                       l.pick_count
                                   ) + 1.96 * 1.96 / 4
                               ) / l.pick_count,
                               1 + 1.96 * 1.96 / l.pick_count
                           ) AS wilson_wr
                    FROM pick_per_lane AS l
                    LEFT JOIN ban_per_champ AS b USING (champion_id)
                    CROSS JOIN denom AS d
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
            SELECT individual_position                            AS team_position,
                   champion_id                                    AS champion_id,
                   COALESCE(ROUND(win_rate, 4), 0)                AS win_rate,
                   COALESCE(ROUND(pick_rate, 4), 0)               AS pick_rate,
                   COALESCE(ROUND(ban_rate, 4), 0)                AS ban_rate,
                   pick_count                                     AS total_games,
                   (0.6 * pct_wilson_wr + 0.4 * pct_presence)     AS tier_score,
                   CASE
                       WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.97 THEN 'S+'
                       WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.90 THEN 'S'
                       WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.75 THEN 'A'
                       WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.50 THEN 'B'
                       WHEN 0.6 * pct_wilson_wr + 0.4 * pct_presence >= 0.20 THEN 'C'
                       ELSE 'D'
                   END                                            AS tier
            FROM ranked
            ORDER BY individual_position, tier_score DESC
            """;

    private ChampionStatsBigQuerySqls() {
    }
}
