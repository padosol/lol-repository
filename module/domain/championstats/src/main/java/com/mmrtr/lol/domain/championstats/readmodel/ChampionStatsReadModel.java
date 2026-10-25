package com.mmrtr.lol.domain.championstats.readmodel;

import java.util.List;

public record ChampionStatsReadModel(
    String tier,
    List<ChampionPositionStatsReadModel> positions
) {}
