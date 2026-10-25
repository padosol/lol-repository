package com.mmrtr.lol.domain.championstats.readmodel;

public record ChampionBootBuildReadModel(
    int bootId,
    long games,
    double winRate,
    double pickRate,
    long sampleSize,
    long totalSampleSize,
    double confidenceLowerBound
) {
    public ChampionBootBuildReadModel(int bootId, long games, double winRate, double pickRate) {
        this(bootId, games, winRate, pickRate, games, 0L, 0.0);
    }

    public ChampionBootBuildReadModel withConfidence(long totalSampleSize, double confidenceLowerBound) {
        return new ChampionBootBuildReadModel(
                bootId, games, winRate, pickRate,
                games, totalSampleSize, confidenceLowerBound);
    }
}
