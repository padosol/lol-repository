package lol.mmrtr.lolrepository.riot.dto.spectator;

import lol.mmrtr.lolrepository.riot.dto.error.ErrorDTO;
import lombok.Getter;

import java.util.List;

@Getter
public class CurrentGameInfo extends ErrorDTO {

    private long gameId;
    private String gameType;
    private long gameStartTime;
    private long mapId;
    private long gameLength;
    private String platformId;
    private String gameMode;
    private List<BannedChampion> bannedChampions;
    private long gameQueueConfigId;
    private Observer observers;
    private List<CurrentGameParticipant> participants;

}
