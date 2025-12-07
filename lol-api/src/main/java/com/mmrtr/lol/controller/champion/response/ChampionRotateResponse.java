package com.mmrtr.lol.controller.champion.response;

import com.mmrtr.lol.riot.dto.champion.ChampionInfo;

import java.util.List;

public record ChampionRotateResponse(
        int maxNewPlayerLevel,
        List<Integer>freeChampionIdsForNewPlayers,
        List<Integer> freeChampionIds
) {
    public static ChampionRotateResponse of(ChampionInfo championInfo) {
        return new ChampionRotateResponse(
                championInfo.getMaxNewPlayerLevel(),
                championInfo.getFreeChampionIdsForNewPlayers(),
                championInfo.getFreeChampionIds()
        );
    }
}
