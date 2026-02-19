package com.mmrtr.lol.controller.champion.response;

import com.mmrtr.lol.domain.champion_stat.domain.ChampionStatSummary;

import java.math.BigDecimal;

public record ChampionStatSummaryResponse(
        int championId,
        String teamPosition,
        long totalGames,
        long wins,
        long totalBans,
        long totalMatchesInDimension,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        BigDecimal avgCs,
        BigDecimal avgGold
) {
    public static ChampionStatSummaryResponse of(ChampionStatSummary domain) {
        return new ChampionStatSummaryResponse(
                domain.getChampionId(),
                domain.getTeamPosition(),
                domain.getTotalGames(),
                domain.getWins(),
                domain.getTotalBans(),
                domain.getTotalMatchesInDimension(),
                domain.getAvgKills(),
                domain.getAvgDeaths(),
                domain.getAvgAssists(),
                domain.getAvgCs(),
                domain.getAvgGold()
        );
    }
}
