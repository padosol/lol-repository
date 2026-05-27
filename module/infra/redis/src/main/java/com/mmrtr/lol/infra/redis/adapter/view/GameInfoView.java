package com.mmrtr.lol.infra.redis.adapter.view;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameInfoView {
    private String dataVersion;
    private long gameCreation;
    private long gameDuration;
    private long gameEndTimestamp;
    private String gameMode;
    private long gameStartTimestamp;
    private String gameType;
    private String gameVersion;
    private int mapId;
    private String platformId;
    private int queueId;
    private String tournamentCode;
    private String matchId;
    private String averageTier;
    private String averageRank;
}
