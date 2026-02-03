package com.mmrtr.lol.infra.riot.dto.spectator;

/**
 * Riot API 게임 참여자 VO
 */
public record ParticipantVO(
    String summonerName,
    String puuid,
    long championId,
    long teamId,
    long spell1Id,
    long spell2Id,
    boolean bot,
    PerksVO perks
) {}
