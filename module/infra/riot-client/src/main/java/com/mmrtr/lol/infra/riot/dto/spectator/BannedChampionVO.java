package com.mmrtr.lol.infra.riot.dto.spectator;

/**
 * Riot API 밴 챔피언 VO
 */
public record BannedChampionVO(
    long championId,
    long teamId,
    int pickTurn
) {}
