package com.mmrtr.lol.domain.championstats.readmodel;

public record ChampionSpellStatsReadModel(
    int summoner1Id,
    int summoner2Id,
    long games,
    double winRate,
    double pickRate,
    long sampleSize,
    long totalSampleSize,
    double confidenceLowerBound
) {
    public ChampionSpellStatsReadModel(int summoner1Id, int summoner2Id,
                                       long games, double winRate, double pickRate) {
        this(summoner1Id, summoner2Id, games, winRate, pickRate, games, 0L, 0.0);
    }

    public ChampionSpellStatsReadModel withConfidence(long totalSampleSize, double confidenceLowerBound) {
        return new ChampionSpellStatsReadModel(
                summoner1Id, summoner2Id, games, winRate, pickRate,
                games, totalSampleSize, confidenceLowerBound);
    }
}
