package com.mmrtr.lol.controller.champion.response;

import com.mmrtr.lol.domain.champion_stat.domain.ChampionSkillStat;

public record ChampionSkillStatResponse(
        int championId,
        String teamPosition,
        String skillOrder,
        String skillPriority,
        long games,
        long wins
) {
    public static ChampionSkillStatResponse of(ChampionSkillStat domain) {
        return new ChampionSkillStatResponse(
                domain.getChampionId(),
                domain.getTeamPosition(),
                domain.getSkillOrder(),
                domain.getSkillPriority(),
                domain.getGames(),
                domain.getWins()
        );
    }
}
