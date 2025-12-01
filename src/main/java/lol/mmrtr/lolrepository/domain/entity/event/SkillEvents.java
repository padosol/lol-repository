package lol.mmrtr.lolrepository.domain.entity.event;

import lol.mmrtr.lolrepository.riot.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SkillEvents {

    private Long id;
    private String matchId;
    private int timelineTimestamp;

    private int skillSlot;
    private int participantId;
    private String levelUpType;
    private long timestamp;
    private String type;

    public SkillEvents(){};

    public SkillEvents(String matchId, int timelineTimestamp, EventsTimeLineDto eventsTimeLineDto) {
        this.matchId = matchId;
        this.timelineTimestamp = timelineTimestamp;
        this.skillSlot = eventsTimeLineDto.getSkillSlot();
        this.participantId = eventsTimeLineDto.getParticipantId();
        this.levelUpType = eventsTimeLineDto.getLevelUpType();
        this.timestamp = eventsTimeLineDto.getTimestamp();
        this.type = eventsTimeLineDto.getType();
    }

}



