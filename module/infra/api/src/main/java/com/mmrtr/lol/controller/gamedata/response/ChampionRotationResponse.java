package com.mmrtr.lol.controller.gamedata.response;

import com.mmrtr.lol.domain.champion.domain.ChampionRotation;
import java.util.List;

public record ChampionRotationResponse(
        int maxNewPlayerLevel,
        List<Integer> freeChampionIdsForNewPlayers,
        List<Integer> freeChampionIds
) {
    public static ChampionRotationResponse of(ChampionRotation rotation) {
        return new ChampionRotationResponse(
                rotation.getMaxNewPlayerLevel(),
                rotation.getFreeChampionIdsForNewPlayers(),
                rotation.getFreeChampionIds());
    }
}
