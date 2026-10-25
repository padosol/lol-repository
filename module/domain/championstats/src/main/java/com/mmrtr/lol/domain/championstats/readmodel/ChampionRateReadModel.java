package com.mmrtr.lol.domain.championstats.readmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** {@code totalGames} 는 tier 계산에만 쓰고 응답·캐시 JSON 에서는 뺀다 (캐시에서 읽으면 0). */
public record ChampionRateReadModel(
        int championId,
        double winRate,
        double pickRate,
        double banRate,
        @JsonIgnore long totalGames,
        String tier
) {
    public ChampionRateReadModel(int championId, double winRate, double pickRate,
                                  double banRate, long totalGames) {
        this(championId, winRate, pickRate, banRate, totalGames, null);
    }

    public ChampionRateReadModel withTier(String tier) {
        return new ChampionRateReadModel(championId, winRate, pickRate, banRate, totalGames, tier);
    }
}
