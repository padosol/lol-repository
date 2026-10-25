package com.mmrtr.lol.domain.championstats.readmodel;

public record ChampionWinRateReadModel(
    String teamPosition,
    long totalGames,
    long totalWins,
    double totalWinRate
) {}
