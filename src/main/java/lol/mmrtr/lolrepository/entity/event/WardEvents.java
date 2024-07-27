package lol.mmrtr.lolrepository.entity.event;

import lol.mmrtr.lolrepository.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WardEvents {

    private Long id;

    private int timelineTimestamp;

    private int participantId;
    private String wardType;

    private long timestamp;
    private String type;


    public WardEvents(){}
    public WardEvents(int timelineTimestamp, EventsTimeLineDto eventsTimeLineDto) {
        this.timelineTimestamp = timelineTimestamp;
        this.participantId = eventsTimeLineDto.getParticipantId();
        this.wardType = eventsTimeLineDto.getWardType();
        this.timestamp = eventsTimeLineDto.getTimestamp();
        this.type = eventsTimeLineDto.getType();
    }


}
