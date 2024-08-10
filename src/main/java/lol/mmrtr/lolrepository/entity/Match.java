package lol.mmrtr.lolrepository.entity;

import lol.mmrtr.lolrepository.riot.dto.match.MatchDto;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Setter
public class Match {

    private String matchId;
    private String dateVersion;
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
    private int season;
    private LocalDateTime gameCreateDatetime;
    private LocalDateTime gameEndDatetime;
    private LocalDateTime gameStartDatetime;

    public Match(){};

    public Match(MatchDto matchDto) {
        this.matchId = matchDto.getMetadata().getMatchId();
        this.dateVersion = matchDto.getMetadata().getDataVersion();
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
        this.season = 23;
        this.gameCreateDatetime = LocalDateTime.ofInstant(Instant.ofEpochMilli(matchDto.getInfo().getGameCreation()), ZoneId.systemDefault());
        this.gameEndDatetime = LocalDateTime.ofInstant(Instant.ofEpochMilli(matchDto.getInfo().getGameEndTimestamp()), ZoneId.systemDefault());
        this.gameStartDatetime = LocalDateTime.ofInstant(Instant.ofEpochMilli(matchDto.getInfo().getGameStartTimestamp()), ZoneId.systemDefault());
    }

}
