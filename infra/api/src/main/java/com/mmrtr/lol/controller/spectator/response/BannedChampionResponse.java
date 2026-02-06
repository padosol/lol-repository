package com.mmrtr.lol.controller.spectator.response;

import com.mmrtr.lol.domain.spectator.domain.BannedChampion;

public record BannedChampionResponse(
        long championId,
        long teamId,
        int pickTurn
) {
    public static BannedChampionResponse of(BannedChampion bannedChampion) {
        return new BannedChampionResponse(
                bannedChampion.getChampionId(),
                bannedChampion.getTeamId(),
                bannedChampion.getPickTurn()
        );
    }
}
