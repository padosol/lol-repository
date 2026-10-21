package com.mmrtr.lol.infra.bigquery.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsQueryPort;
import com.mmrtr.lol.domain.championstats.domain.TierFilter;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionAverageStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionBootBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionMatchupReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRuneBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSkillBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSpellStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStartItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionWinRateReadModel;
import com.mmrtr.lol.infra.bigquery.config.BigQueryProperties;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** lol-server {@code ChampionStatsBigQueryAdapter} 이전. 쿼리·파라미터·행 매핑이 같다. */
@Component
public class ChampionStatsBigQueryAdapter implements ChampionStatsQueryPort {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Integer>> INT_LIST_TYPE = new TypeReference<>() {};

    private static final String TEAM_POSITION = "team_position";
    private static final String GAMES = "games";
    private static final String WIN_RATE = "win_rate";
    private static final String PICK_RATE = "pick_rate";
    private static final String CHAMPION_ID = "championId";

    private final BigQuery bigQuery;
    private final String dataset;

    public ChampionStatsBigQueryAdapter(BigQuery bigQuery, BigQueryProperties properties) {
        this.bigQuery = bigQuery;
        this.dataset = properties.dataset();
    }

    @Override
    public List<ChampionWinRateReadModel> getChampionWinRates(
            int championId, String patch, String platformId, TierFilter tierFilter) {
        String sql = ChampionStatsBigQuerySqls.WIN_RATES.formatted(table("mv_champion_pick_stats"));

        QueryJobConfiguration job = baseQuery(sql, patch, platformId, tierFilter)
                .addNamedParameter(CHAMPION_ID, QueryParameterValue.int64(championId))
                .build();

        return query(job, row -> new ChampionWinRateReadModel(
                row.get(TEAM_POSITION).getStringValue(),
                row.get("total_games").getLongValue(),
                row.get("total_wins").getLongValue(),
                row.get("total_win_rate").getDoubleValue()
        ));
    }

    @Override
    public List<ChampionAverageStatsReadModel> getChampionAverageStats(
            int championId, String patch, String platformId, TierFilter tierFilter) {
        String sql = ChampionStatsBigQuerySqls.AVG_STATS.formatted(table("match_participant_fact"));

        QueryJobConfiguration job = baseQuery(sql, patch, platformId, tierFilter)
                .addNamedParameter(CHAMPION_ID, QueryParameterValue.int64(championId))
                .build();

        return query(job, row -> new ChampionAverageStatsReadModel(
                row.get(TEAM_POSITION).getStringValue(),
                row.get("avg_kills").getDoubleValue(),
                row.get("avg_deaths").getDoubleValue(),
                row.get("avg_assists").getDoubleValue(),
                row.get("kda").getDoubleValue(),
                row.get("avg_gold_per_minute").getDoubleValue(),
                row.get("avg_lane_cs_10m").getDoubleValue(),
                row.get("avg_jungle_cs_10m").getDoubleValue()
        ));
    }

    @Override
    public List<ChampionMatchupReadModel> getChampionMatchups(
            int championId, String patch, String platformId, TierFilter tierFilter, String position) {
        String sql = ChampionStatsBigQuerySqls.MATCHUPS.formatted(table("mv_champion_matchup_stats"));

        return query(championPositionQuery(sql, patch, platformId, tierFilter, championId, position),
                row -> new ChampionMatchupReadModel(
                        row.get("rank_type").getStringValue(),
                        getInt(row, "opponent_champion_id"),
                        row.get(GAMES).getLongValue(),
                        row.get(WIN_RATE).getDoubleValue(),
                        row.get(PICK_RATE).getDoubleValue()
                ));
    }

    @Override
    public List<ChampionRuneBuildReadModel> getChampionRuneBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position) {
        String sql = ChampionStatsBigQuerySqls.RUNE_BUILDS.formatted(table("mv_champion_rune_stats"));

        return query(championPositionQuery(sql, patch, platformId, tierFilter, championId, position),
                row -> new ChampionRuneBuildReadModel(
                        getInt(row, "primary_style_id"),
                        getInt(row, "sub_style_id"),
                        getInt(row, "primary_perk0"),
                        getInt(row, "primary_perk1"),
                        getInt(row, "primary_perk2"),
                        getInt(row, "primary_perk3"),
                        getInt(row, "sub_perk0"),
                        getInt(row, "sub_perk1"),
                        row.get(GAMES).getLongValue(),
                        row.get(WIN_RATE).getDoubleValue(),
                        row.get(PICK_RATE).getDoubleValue()
                ));
    }

    @Override
    public List<ChampionSpellStatsReadModel> getChampionSpellStats(
            int championId, String patch, String platformId, TierFilter tierFilter, String position) {
        String sql = ChampionStatsBigQuerySqls.SPELL_STATS.formatted(table("mv_champion_spell_stats"));

        return query(championPositionQuery(sql, patch, platformId, tierFilter, championId, position),
                row -> new ChampionSpellStatsReadModel(
                        getInt(row, "summoner1id"),
                        getInt(row, "summoner2id"),
                        row.get(GAMES).getLongValue(),
                        row.get(WIN_RATE).getDoubleValue(),
                        row.get(PICK_RATE).getDoubleValue()
                ));
    }

    @Override
    public List<ChampionSkillBuildReadModel> getChampionSkillBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position) {
        String sql = ChampionStatsBigQuerySqls.SKILL_BUILDS.formatted(table("mv_champion_skill_stats"));

        return query(championPositionQuery(sql, patch, platformId, tierFilter, championId, position),
                row -> new ChampionSkillBuildReadModel(
                        row.get("skill_build").getStringValue(),
                        row.get(GAMES).getLongValue(),
                        row.get(WIN_RATE).getDoubleValue(),
                        row.get(PICK_RATE).getDoubleValue()
                ));
    }

    @Override
    public List<ChampionStartItemBuildReadModel> getChampionStartItemBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position) {
        String sql = ChampionStatsBigQuerySqls.START_ITEM_BUILDS.formatted(table("mv_champion_start_item_stats"));

        return query(championPositionQuery(sql, patch, platformId, tierFilter, championId, position),
                row -> new ChampionStartItemBuildReadModel(
                        parseIntArrayJson(row.get("start_items").getStringValue()),
                        row.get(GAMES).getLongValue(),
                        row.get(WIN_RATE).getDoubleValue(),
                        row.get(PICK_RATE).getDoubleValue()
                ));
    }

    @Override
    public List<ChampionBootBuildReadModel> getChampionBootBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position) {
        String sql = ChampionStatsBigQuerySqls.BOOT_BUILDS.formatted(table("mv_champion_boot_stats"));

        return query(championPositionQuery(sql, patch, platformId, tierFilter, championId, position),
                row -> new ChampionBootBuildReadModel(
                        getInt(row, "boot_id"),
                        row.get(GAMES).getLongValue(),
                        row.get(WIN_RATE).getDoubleValue(),
                        row.get(PICK_RATE).getDoubleValue()
                ));
    }

    @Override
    public List<ChampionItemBuildReadModel> getChampionItemBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position) {
        String sql = ChampionStatsBigQuerySqls.ITEM_BUILDS.formatted(table("mv_champion_item_build_stats"));

        return query(championPositionQuery(sql, patch, platformId, tierFilter, championId, position),
                row -> new ChampionItemBuildReadModel(
                        parseIntArrayJson(row.get("item_build").getStringValue()),
                        row.get(GAMES).getLongValue(),
                        row.get(WIN_RATE).getDoubleValue(),
                        row.get(PICK_RATE).getDoubleValue()
                ));
    }

    @Override
    public Map<String, List<ChampionRateReadModel>> getChampionStatsByPosition(
            String patch, String platformId, TierFilter tierFilter) {
        String sql = ChampionStatsBigQuerySqls.STATS_BY_POSITION.formatted(
                table("mv_match_count_stats"),
                table("mv_champion_ban_stats"),
                table("mv_champion_pick_stats"));

        List<AbstractMap.SimpleEntry<String, ChampionRateReadModel>> rows = query(
                baseQuery(sql, patch, platformId, tierFilter).build(),
                row -> new AbstractMap.SimpleEntry<>(
                        row.get(TEAM_POSITION).getStringValue(),
                        new ChampionRateReadModel(
                                getInt(row, "champion_id"),
                                row.get(WIN_RATE).getDoubleValue(),
                                row.get(PICK_RATE).getDoubleValue(),
                                row.get("ban_rate").getDoubleValue(),
                                row.get("total_games").getLongValue(),
                                row.get("tier").getStringValue()
                        )
                ));

        // 포지션 순서는 쿼리 정렬(포지션, tier 점수 내림차순)을 따른다
        return rows.stream().collect(Collectors.groupingBy(
                Map.Entry::getKey,
                LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
        ));
    }

    private String table(String name) {
        return "`" + dataset + "." + name + "`";
    }

    private QueryJobConfiguration.Builder baseQuery(
            String sql, String patch, String platformId, TierFilter tierFilter) {
        return QueryJobConfiguration.newBuilder(sql)
                .setUseLegacySql(false)
                .addNamedParameter("patch", QueryParameterValue.int64(toPatchVersionInt(patch)))
                .addNamedParameter("platform", QueryParameterValue.string(platformId))
                .addNamedParameter("tierBuckets", QueryParameterValue.array(
                        toTierBuckets(tierFilter), StandardSQLTypeName.INT64));
    }

    private QueryJobConfiguration championPositionQuery(
            String sql, String patch, String platformId, TierFilter tierFilter,
            int championId, String position) {
        return baseQuery(sql, patch, platformId, tierFilter)
                .addNamedParameter(CHAMPION_ID, QueryParameterValue.int64(championId))
                .addNamedParameter("position", QueryParameterValue.string(position))
                .build();
    }

    /** {@code 14.24} → {@code 1424}. 형식은 서비스가 먼저 검증한다. */
    static long toPatchVersionInt(String patch) {
        String[] parts = patch.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Unsupported patch format: " + patch);
        }
        try {
            return Long.parseLong(parts[0]) * 100 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unsupported patch format: " + patch, e);
        }
    }

    /** BigQuery 의 {@code tier_bucket} 은 Tier 점수 (CHALLENGER=10000 … IRON=1000). */
    private static Long[] toTierBuckets(TierFilter tierFilter) {
        return tierFilter.getTiers().stream()
                .map(tier -> (long) tier.getScore())
                .toArray(Long[]::new);
    }

    private static int getInt(FieldValueList row, String column) {
        return (int) row.get(column).getLongValue();
    }

    private static List<Integer> parseIntArrayJson(String json) {
        try {
            return MAPPER.readValue(json, INT_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse int array: " + json, e);
        }
    }

    private <T> List<T> query(QueryJobConfiguration job, Function<FieldValueList, T> mapper) {
        TableResult result;
        try {
            result = bigQuery.query(job);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BigQuery query interrupted", e);
        }
        List<T> rows = new ArrayList<>();
        for (FieldValueList row : result.iterateAll()) {
            rows.add(mapper.apply(row));
        }
        return rows;
    }
}
