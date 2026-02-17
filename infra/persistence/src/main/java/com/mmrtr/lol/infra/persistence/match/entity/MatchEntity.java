package com.mmrtr.lol.infra.persistence.match.entity;


import com.mmrtr.lol.infra.riot.dto.match.MatchDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "match")
public class MatchEntity {

    @Id
    @Comment("매치 ID")
    @Column(name = "match_id")
    private String matchId;

    @Comment("데이터 버전")
    @Column(name = "data_version")
    private String dataVersion;

    // info
    @Comment("게임 종료 결과")
    private String endOfGameResult;
    @Comment("게임 생성 타임스탬프")
    private	long gameCreation;
    @Comment("게임 진행 시간 (초)")
    private	long gameDuration;
    @Comment("게임 종료 타임스탬프")
    private	long gameEndTimestamp;
    @Comment("게임 시작 타임스탬프")
    private	long gameStartTimestamp;
    @Comment("게임 ID")
    private	long gameId;
    @Comment("게임 모드")
    private	String gameMode;
    @Comment("게임 이름")
    private	String gameName;
    @Comment("게임 타입")
    private	String gameType;

    @Comment("게임 버전")
    private	String gameVersion;

    @Comment("맵 ID")
    private	int mapId;
    @Comment("큐 ID")
    private	int queueId;
    @Comment("플랫폼 ID")
    private	String platformId;
    @Comment("토너먼트 코드")
    private	String tournamentCode;

    // 시즌
    @Comment("시즌")
    private int season;

    // date time
    @Comment("게임 생성 일시")
    private LocalDateTime gameCreateDatetime;
    @Comment("게임 종료 일시")
    private LocalDateTime gameEndDatetime;
    @Comment("게임 시작 일시")
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
