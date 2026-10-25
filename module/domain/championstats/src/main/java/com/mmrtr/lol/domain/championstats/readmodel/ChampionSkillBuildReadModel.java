package com.mmrtr.lol.domain.championstats.readmodel;

public record ChampionSkillBuildReadModel(
    String skillBuild,
    long games,
    double winRate,
    double pickRate,
    long sampleSize,
    long totalSampleSize,
    double confidenceLowerBound
) {
    public ChampionSkillBuildReadModel(String skillBuild, long games, double winRate, double pickRate) {
        this(skillBuild, games, winRate, pickRate, games, 0L, 0.0);
    }

    public ChampionSkillBuildReadModel withConfidence(long totalSampleSize, double confidenceLowerBound) {
        return new ChampionSkillBuildReadModel(
                skillBuild, games, winRate, pickRate,
                games, totalSampleSize, confidenceLowerBound);
    }
}
