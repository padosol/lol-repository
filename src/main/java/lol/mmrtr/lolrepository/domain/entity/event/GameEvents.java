package lol.mmrtr.lolrepository.domain.entity.event;

import lol.mmrtr.lolrepository.riot.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameEvents {

    private Long id;
    private int timelineTimestamp;

    private long timestamp;
    private long gameId;
    private long realTimestamp;
    private int winningTeam;
    private String type;

    public GameEvents(){}

    public GameEvents(int timelineTimestamp, EventsTimeLineDto eventsTimeLineDto){
        this.timelineTimestamp = timelineTimestamp;
        this.timestamp = eventsTimeLineDto.getTimestamp();
        this.gameId = eventsTimeLineDto.getGameId();
        this.realTimestamp = eventsTimeLineDto.getRealTimestamp();
        this.winningTeam = eventsTimeLineDto.getWinningTeam();
        this.type = eventsTimeLineDto.getType();
    }

}
