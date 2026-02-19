package com.mmrtr.lol.controller.champion.response;

import com.mmrtr.lol.domain.champion_stat.domain.ChampionSpellStat;

public record ChampionSpellStatResponse(
        int championId,
        String teamPosition,
        int spell1Id,
        int spell2Id,
        long games,
        long wins
) {
    public static ChampionSpellStatResponse of(ChampionSpellStat domain) {
        return new ChampionSpellStatResponse(
                domain.getChampionId(),
                domain.getTeamPosition(),
                domain.getSpell1Id(),
                domain.getSpell2Id(),
                domain.getGames(),
                domain.getWins()
        );
    }
}
