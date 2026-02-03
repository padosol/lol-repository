package com.mmrtr.lol.infra.riot.dto.spectator;

import java.util.List;

/**
 * Riot API 현재 게임 정보 VO
 */
public record CurrentGameInfoVO(
    long gameId,
    String gameType,
    String gameMode,
    long mapId,
    long gameStartTime,
    long gameLength,
    String platformId,
    String encryptionKey,
    List<ParticipantVO> participants,
    List<BannedChampionVO> bannedChampions
) {}
