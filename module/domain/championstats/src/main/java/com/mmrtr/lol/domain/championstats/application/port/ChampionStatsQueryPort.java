package com.mmrtr.lol.domain.championstats.application.port;

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

import java.util.List;
import java.util.Map;

/**
 * 챔피언 통계 집계 조회 (BigQuery). {@code patch} 는 {@code 14.24} 형식, {@code platformId} 는 Riot platformId({@code KR}).
 */
public interface ChampionStatsQueryPort {

    /** 포지션 → 그 포지션 챔피언 목록 (BigQuery 가 정한 순서·tier 그대로). */
    Map<String, List<ChampionRateReadModel>> getChampionStatsByPosition(
            String patch, String platformId, TierFilter tierFilter);

    List<ChampionWinRateReadModel> getChampionWinRates(
            int championId, String patch, String platformId, TierFilter tierFilter);

    List<ChampionAverageStatsReadModel> getChampionAverageStats(
            int championId, String patch, String platformId, TierFilter tierFilter);

    List<ChampionMatchupReadModel> getChampionMatchups(
            int championId, String patch, String platformId, TierFilter tierFilter, String position);

    List<ChampionRuneBuildReadModel> getChampionRuneBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position);

    List<ChampionSpellStatsReadModel> getChampionSpellStats(
            int championId, String patch, String platformId, TierFilter tierFilter, String position);

    List<ChampionSkillBuildReadModel> getChampionSkillBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position);

    List<ChampionStartItemBuildReadModel> getChampionStartItemBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position);

    List<ChampionBootBuildReadModel> getChampionBootBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position);

    List<ChampionItemBuildReadModel> getChampionItemBuilds(
            int championId, String patch, String platformId, TierFilter tierFilter, String position);
}
