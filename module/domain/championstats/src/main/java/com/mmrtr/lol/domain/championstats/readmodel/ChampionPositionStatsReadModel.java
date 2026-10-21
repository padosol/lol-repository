package com.mmrtr.lol.domain.championstats.readmodel;

import java.util.List;

public record ChampionPositionStatsReadModel(
    String teamPosition,
    double winRate,
    double pickRate,
    double banRate,
    String tier,
    long totalGames,
    ChampionAverageStatsReadModel averages,
    List<ChampionMatchupReadModel> matchups,
    List<ChampionRuneBuildReadModel> runeBuilds,
    List<ChampionSpellStatsReadModel> spellStats,
    List<ChampionSkillBuildReadModel> skillBuilds,
    List<ChampionStartItemBuildReadModel> startItemBuilds,
    List<ChampionBootBuildReadModel> bootBuilds,
    List<ChampionItemBuildReadModel> itemBuilds
) {}
