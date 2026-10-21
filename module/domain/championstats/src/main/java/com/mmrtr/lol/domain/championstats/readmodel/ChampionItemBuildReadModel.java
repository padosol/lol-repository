package com.mmrtr.lol.domain.championstats.readmodel;

import java.util.List;

public record ChampionItemBuildReadModel(
    List<Integer> itemBuild,
    long games,
    double winRate,
    double pickRate,
    long sampleSize,
    long totalSampleSize,
    double confidenceLowerBound
) {
    public ChampionItemBuildReadModel(List<Integer> itemBuild,
                                      long games, double winRate, double pickRate) {
        this(itemBuild, games, winRate, pickRate, games, 0L, 0.0);
    }

    public ChampionItemBuildReadModel withConfidence(long totalSampleSize, double confidenceLowerBound) {
        return new ChampionItemBuildReadModel(
                itemBuild, games, winRate, pickRate,
                games, totalSampleSize, confidenceLowerBound);
    }
}
