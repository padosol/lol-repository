package com.mmrtr.lol.domain.spectator.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ActiveGame {
    private long gameId;
    private String gameType;
    private String gameMode;
    private long mapId;
    private long gameStartTime;
    private long gameLength;
    private String platformId;
    private int gameQueueConfigId;
    private String encryptionKey;
    private List<GameParticipant> participants;
    private List<BannedChampion> bannedChampions;
}
