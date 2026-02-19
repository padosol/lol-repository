package com.mmrtr.lol.controller.champion.response;

import com.mmrtr.lol.domain.champion_stat.domain.ChampionRuneStat;

public record ChampionRuneStatResponse(
        int championId,
        String teamPosition,
        int primaryRuneId,
        String primaryRuneIds,
        int secondaryRuneId,
        String secondaryRuneIds,
        int statOffense,
        int statFlex,
        int statDefense,
        long games,
        long wins
) {
    public static ChampionRuneStatResponse of(ChampionRuneStat domain) {
        return new ChampionRuneStatResponse(
                domain.getChampionId(),
                domain.getTeamPosition(),
                domain.getPrimaryRuneId(),
                domain.getPrimaryRuneIds(),
                domain.getSecondaryRuneId(),
                domain.getSecondaryRuneIds(),
                domain.getStatOffense(),
                domain.getStatFlex(),
                domain.getStatDefense(),
                domain.getGames(),
                domain.getWins()
        );
    }
}
