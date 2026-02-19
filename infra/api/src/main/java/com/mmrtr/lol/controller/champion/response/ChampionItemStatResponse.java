package com.mmrtr.lol.controller.champion.response;

import com.mmrtr.lol.domain.champion_stat.domain.ChampionItemStat;

public record ChampionItemStatResponse(
        int championId,
        String teamPosition,
        String buildType,
        String itemIds,
        long games,
        long wins
) {
    public static ChampionItemStatResponse of(ChampionItemStat domain) {
        return new ChampionItemStatResponse(
                domain.getChampionId(),
                domain.getTeamPosition(),
                domain.getBuildType(),
                domain.getItemIds(),
                domain.getGames(),
                domain.getWins()
        );
    }
}
