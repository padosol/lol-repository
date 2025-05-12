package lol.mmrtr.lolrepository.domain.entity.event;

import lol.mmrtr.lolrepository.riot.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LevelEvents {

    private Long id;
    private int timelineTimestamp;

    private int level;
    private int participantId;
    private long timestamp;
    private String type;

    public LevelEvents(){}

    public LevelEvents(int timelineTimestamp, EventsTimeLineDto eventsTimeLineDto){
        this.timelineTimestamp = timelineTimestamp;
        this.level = eventsTimeLineDto.getLevel();
        this.participantId = eventsTimeLineDto.getParticipantId();
        this.timestamp = eventsTimeLineDto.getTimestamp();
        this.type = eventsTimeLineDto.getType();

    }
}
