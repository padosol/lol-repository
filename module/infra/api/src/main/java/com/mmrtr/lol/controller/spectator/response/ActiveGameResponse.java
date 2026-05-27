package com.mmrtr.lol.controller.spectator.response;

import com.mmrtr.lol.domain.spectator.domain.ActiveGame;

import java.util.List;
import java.util.stream.Collectors;

public record ActiveGameResponse(
        long gameId,
        String gameType,
        String gameMode,
        long mapId,
        long gameStartTime,
        long gameLength,
        String platformId,
        int gameQueueConfigId,
        String encryptionKey,
        List<ParticipantResponse> participants,
        List<BannedChampionResponse> bannedChampions
) {
    public static ActiveGameResponse of(ActiveGame activeGame) {
        return new ActiveGameResponse(
                activeGame.getGameId(),
                activeGame.getGameType(),
                activeGame.getGameMode(),
                activeGame.getMapId(),
                activeGame.getGameStartTime(),
                activeGame.getGameLength(),
                activeGame.getPlatformId(),
                activeGame.getGameQueueConfigId(),
                activeGame.getEncryptionKey(),
                activeGame.getParticipants().stream()
                        .map(ParticipantResponse::of)
                        .collect(Collectors.toList()),
                activeGame.getBannedChampions().stream()
                        .map(BannedChampionResponse::of)
                        .collect(Collectors.toList())
        );
    }
}
