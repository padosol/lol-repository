package com.mmrtr.lol.controller.champion.response;

import com.mmrtr.lol.domain.champion_stat.domain.ChampionMatchupStat;

import java.math.BigDecimal;

public record ChampionMatchupStatResponse(
        int championId,
        String teamPosition,
        int opponentChampionId,
        long games,
        long wins,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        BigDecimal avgGoldDiff
) {
    public static ChampionMatchupStatResponse of(ChampionMatchupStat domain) {
        return new ChampionMatchupStatResponse(
                domain.getChampionId(),
                domain.getTeamPosition(),
                domain.getOpponentChampionId(),
                domain.getGames(),
                domain.getWins(),
                domain.getAvgKills(),
                domain.getAvgDeaths(),
                domain.getAvgAssists(),
                domain.getAvgGoldDiff()
        );
    }
}
