package lol.mmrtr.lolrepository.entity.event;

import lol.mmrtr.lolrepository.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ItemEvents {

    private Long id;
    private String matchId;
    private int timelineTimestamp;

    private int itemId;
    private int participantId;
    private long timestamp;
    private String type;

    private int afterId;
    private int beforeId;
    private int goldGain;

    public ItemEvents(){};
    public ItemEvents(String matchId, int timelineTimestamp, EventsTimeLineDto eventsTimeLineDto) {

        this.matchId = matchId;
        this.timelineTimestamp = timelineTimestamp;
        this.itemId = eventsTimeLineDto.getItemId();
        this.participantId = eventsTimeLineDto.getParticipantId();
        this.timestamp = eventsTimeLineDto.getTimestamp();
        this.type = eventsTimeLineDto.getType();
        this.afterId = eventsTimeLineDto.getAfterId();
        this.beforeId = eventsTimeLineDto.getBeforeId();
        this.goldGain = eventsTimeLineDto.getGoldGain();

    }

}
