package com.mmrtr.lol.domain.match.entity;


import com.mmrtr.lol.riot.dto.match.MatchDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "match")
public class MatchEntity {

    @Id
    @Column(name = "match_id")
    private String matchId;

    @Column(name = "data_version")
    private String dataVersion;

//    @BatchSize(size = 100)
//    @OneToMany(mappedBy = "matchEntity", fetch = FetchType.LAZY)
//    private List<MatchSummonerEntity> matchSummonerEntities;

    // info
    private String endOfGameResult;
    private	long gameCreation;
    private	long gameDuration;
    private	long gameEndTimestamp;
    private	long gameStartTimestamp;
    private	long gameId;
    private	String gameMode;
    private	String gameName;
    private	String gameType;

    private	String gameVersion;

    private	int mapId;
    private	int queueId;
    private	String platformId;
    private	String tournamentCode;

    // 시즌
    private int season;

    // date time
    private LocalDateTime gameCreateDatetime;
    private LocalDateTime gameEndDatetime;
    private LocalDateTime gameStartDatetime;

    public MatchEntity(MatchDto matchDto, int season) {
        this.matchId = matchDto.getMetadata().getMatchId();
        this.dataVersion = matchDto.getMetadata().getDataVersion();
        this.endOfGameResult = matchDto.getInfo().getEndOfGameResult();
        this.gameCreation = matchDto.getInfo().getGameCreation();
        this.gameDuration = matchDto.getInfo().getGameDuration();
        this.gameEndTimestamp = matchDto.getInfo().getGameEndTimestamp();
        this.gameStartTimestamp = matchDto.getInfo().getGameStartTimestamp();
        this.gameId = matchDto.getInfo().getGameId();
        this.gameMode = matchDto.getInfo().getGameMode();
        this.gameName = matchDto.getInfo().getGameName();
        this.gameType = matchDto.getInfo().getGameType();
        this.gameVersion = matchDto.getInfo().getGameVersion();
        this.mapId = matchDto.getInfo().getMapId();
        this.queueId = matchDto.getInfo().getQueueId();
        this.platformId = matchDto.getInfo().getPlatformId();
        this.tournamentCode = matchDto.getInfo().getTournamentCode();
        this.season = season;
        this.gameCreateDatetime = LocalDateTime.ofInstant(Instant.ofEpochMilli(matchDto.getInfo().getGameCreation()), ZoneId.systemDefault());
        this.gameEndDatetime = LocalDateTime.ofInstant(Instant.ofEpochMilli(matchDto.getInfo().getGameEndTimestamp()), ZoneId.systemDefault());
        this.gameStartDatetime = LocalDateTime.ofInstant(Instant.ofEpochMilli(matchDto.getInfo().getGameStartTimestamp()), ZoneId.systemDefault());
    }

}
