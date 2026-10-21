package com.mmrtr.lol.domain.championstats.readmodel;

import java.util.List;

public record PositionChampionStatsReadModel(
        String teamPosition,
        List<ChampionRateReadModel> champions
) {}
