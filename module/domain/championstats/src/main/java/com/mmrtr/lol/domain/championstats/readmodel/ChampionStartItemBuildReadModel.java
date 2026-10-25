package com.mmrtr.lol.domain.championstats.readmodel;

import java.util.List;

public record ChampionStartItemBuildReadModel(
    List<Integer> startItems,
    long games,
    double winRate,
    double pickRate,
    long sampleSize,
    long totalSampleSize,
    double confidenceLowerBound
) {
    public ChampionStartItemBuildReadModel(List<Integer> startItems,
                                           long games, double winRate, double pickRate) {
        this(startItems, games, winRate, pickRate, games, 0L, 0.0);
    }

    public ChampionStartItemBuildReadModel withConfidence(long totalSampleSize, double confidenceLowerBound) {
        return new ChampionStartItemBuildReadModel(
                startItems, games, winRate, pickRate,
                games, totalSampleSize, confidenceLowerBound);
    }
}
