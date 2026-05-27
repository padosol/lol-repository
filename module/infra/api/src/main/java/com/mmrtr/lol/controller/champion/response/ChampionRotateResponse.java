package com.mmrtr.lol.controller.champion.response;

import com.mmrtr.lol.domain.champion.domain.ChampionRotation;

import java.util.List;

public record ChampionRotateResponse(
        int maxNewPlayerLevel,
        List<Integer> freeChampionIdsForNewPlayers,
        List<Integer> freeChampionIds
) {
    public static ChampionRotateResponse of(ChampionRotation championRotation) {
        return new ChampionRotateResponse(
                championRotation.getMaxNewPlayerLevel(),
                championRotation.getFreeChampionIdsForNewPlayers(),
                championRotation.getFreeChampionIds()
        );
    }
}
