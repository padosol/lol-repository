package lol.mmrtr.lolrepository.domain.entity.event;


import lol.mmrtr.lolrepository.riot.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurretPlateDestroyedEvent {

    private Long id;
    private int timelineTimestamp;

    private int killerId;
    private String laneType;

    private int x;
    private int y;

    private int teamId;
    private long timestamp;
    private String type;

    public TurretPlateDestroyedEvent(){}

    public TurretPlateDestroyedEvent(int timelineTimestamp, EventsTimeLineDto eventsTimeLineDto){

        this.timelineTimestamp = timelineTimestamp;
        this.killerId = eventsTimeLineDto.getKillerId();
        this.laneType = eventsTimeLineDto.getLaneType();
        this.x = eventsTimeLineDto.getPosition().getX();
        this.y = eventsTimeLineDto.getPosition().getY();
        this.teamId = eventsTimeLineDto.getTeamId();
        this.timestamp = eventsTimeLineDto.getTimestamp();
        this.type = eventsTimeLineDto.getType();

    }
}
