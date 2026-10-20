package com.mmrtr.lol.domain.spectator.readmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mmrtr.lol.domain.spectator.domain.ActiveGame;
import com.mmrtr.lol.domain.spectator.domain.BannedChampion;
import com.mmrtr.lol.domain.spectator.domain.GameParticipant;
import com.mmrtr.lol.domain.spectator.domain.GamePerks;

import java.util.List;

/**
 * 진행 중인 게임. 필드·순서가 lol-server {@code CurrentGameInfoReadModel} 과 같다.
 * 캐시에도 이 모양 그대로 저장한다.
 *
 * @param gameStartTime 게임 시작 시각 (epoch ms)
 * @param gameLength    게임 진행 시간 (초)
 */
public record ActiveGameReadModel(
        long gameId,
        String gameType,
        String gameMode,
        long mapId,
        long gameQueueConfigId,
        long gameStartTime,
        long gameLength,
        String platformId,
        String encryptionKey,
        List<Participant> participants,
        List<Banned> bannedChampions
) {

    /** @param isBot JSON 키가 lol-server 와 같은 {@code isBot} 이어야 한다 */
    public record Participant(
            String riotId,
            String puuid,
            long championId,
            long teamId,
            long spell1Id,
            long spell2Id,
            long profileIconId,
            @JsonProperty("isBot") boolean isBot,
            Perks perks
    ) {
    }

    public record Perks(long perkStyle, long perkSubStyle, List<Long> perkIds) {
    }

    public record Banned(long championId, long teamId, int pickTurn) {
    }

    public static ActiveGameReadModel from(ActiveGame game) {
        return new ActiveGameReadModel(
                game.getGameId(),
                game.getGameType(),
                game.getGameMode(),
                game.getMapId(),
                game.getGameQueueConfigId(),
                game.getGameStartTime(),
                game.getGameLength(),
                game.getPlatformId(),
                game.getEncryptionKey(),
                game.getParticipants().stream().map(ActiveGameReadModel::participant).toList(),
                game.getBannedChampions().stream().map(ActiveGameReadModel::banned).toList()
        );
    }

    private static Participant participant(GameParticipant participant) {
        GamePerks perks = participant.getPerks();
        return new Participant(
                participant.getRiotId(),
                participant.getPuuid(),
                participant.getChampionId(),
                participant.getTeamId(),
                participant.getSpell1Id(),
                participant.getSpell2Id(),
                participant.getProfileIconId(),
                participant.isBot(),
                perks != null ? new Perks(perks.getPerkStyle(), perks.getPerkSubStyle(), perks.getPerkIds()) : null
        );
    }

    private static Banned banned(BannedChampion banned) {
        return new Banned(banned.getChampionId(), banned.getTeamId(), banned.getPickTurn());
    }
}
