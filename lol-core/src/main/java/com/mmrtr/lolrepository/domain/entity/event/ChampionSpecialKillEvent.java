package com.mmrtr.lolrepository.domain.entity.event;

import com.mmrtr.lolrepository.riot.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChampionSpecialKillEvent {

    private Long id;
    private int timelineTimestamp;

    private String killType;
    private int killerId;
    private int multiKillLength;

    private int x;
    private int y;

    private long timestamp;
    private String type;

    public ChampionSpecialKillEvent(){}
    public ChampionSpecialKillEvent(int timelineTimestamp, EventsTimeLineDto eventsTimeLineDto){
        this.timelineTimestamp = timelineTimestamp;
        this.killType = eventsTimeLineDto.getKillType();
        this.killerId = eventsTimeLineDto.getKillerId();
        this.multiKillLength = eventsTimeLineDto.getMultiKillLength();
        this.x = eventsTimeLineDto.getPosition().getX();
        this.y = eventsTimeLineDto.getPosition().getY();
        this.timestamp = eventsTimeLineDto.getTimestamp();
        this.type = eventsTimeLineDto.getType();
    }
}
