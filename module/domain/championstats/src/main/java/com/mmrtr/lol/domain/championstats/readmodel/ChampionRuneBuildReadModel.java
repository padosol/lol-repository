package com.mmrtr.lol.domain.championstats.readmodel;

public record ChampionRuneBuildReadModel(
    int primaryStyleId,
    int subStyleId,
    int primaryPerk0,
    int primaryPerk1,
    int primaryPerk2,
    int primaryPerk3,
    int subPerk0,
    int subPerk1,
    long games,
    double winRate,
    double pickRate,
    long sampleSize,
    long totalSampleSize,
    double confidenceLowerBound
) {
    public ChampionRuneBuildReadModel(
            int primaryStyleId, int subStyleId,
            int primaryPerk0, int primaryPerk1, int primaryPerk2, int primaryPerk3,
            int subPerk0, int subPerk1,
            long games, double winRate, double pickRate) {
        this(primaryStyleId, subStyleId,
             primaryPerk0, primaryPerk1, primaryPerk2, primaryPerk3,
             subPerk0, subPerk1,
             games, winRate, pickRate,
             games, 0L, 0.0);
    }

    public ChampionRuneBuildReadModel withConfidence(long totalSampleSize, double confidenceLowerBound) {
        return new ChampionRuneBuildReadModel(
                primaryStyleId, subStyleId,
                primaryPerk0, primaryPerk1, primaryPerk2, primaryPerk3,
                subPerk0, subPerk1,
                games, winRate, pickRate,
                games, totalSampleSize, confidenceLowerBound);
    }
}
