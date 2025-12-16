package com.mmrtr.lol.domain.entity.event;

import com.mmrtr.lol.riot.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KillEvents {

    private Long id;
    private int timelineTimestamp;

    private String assistingParticipantIds;
    private int bounty;
    private int killStreakLength;
    private int killerId;

    private int x;
    private int y;

    private int shutdownBounty;
    private int victimId;

    private long timestamp;
    private String type;

    public KillEvents(){}

    public KillEvents(int timelineTimestamp, EventsTimeLineDto eventsTimeLineDto){

        this.timelineTimestamp = timelineTimestamp;
//        this.assistingParticipantIds = eventsTimeLineDto.getAssistingParticipantIds();
        this.bounty = eventsTimeLineDto.getBounty();
        this.killStreakLength = eventsTimeLineDto.getKillStreakLength();
        this.killerId = eventsTimeLineDto.getKillerId();
        this.x = eventsTimeLineDto.getPosition().getX();
        this.y = eventsTimeLineDto.getPosition().getY();
        this.shutdownBounty = eventsTimeLineDto.getShutdownBounty();
        this.victimId = eventsTimeLineDto.getVictimId();
        this.timestamp = eventsTimeLineDto.getTimestamp();
        this.type = eventsTimeLineDto.getType();

    }
}
