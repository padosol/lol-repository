package com.mmrtr.lol.domain.championstats.readmodel;

public record ChampionMatchupReadModel(
    String rankType,
    int opponentChampionId,
    long games,
    double winRate,
    double pickRate
) {
    public static ChampionMatchupReadModel top(int opponentChampionId, long games, double winRate, double pickRate) {
        return new ChampionMatchupReadModel("TOP", opponentChampionId, games, winRate, pickRate);
    }

    public static ChampionMatchupReadModel bottom(int opponentChampionId, long games, double winRate, double pickRate) {
        return new ChampionMatchupReadModel("BOTTOM", opponentChampionId, games, winRate, pickRate);
    }
}
