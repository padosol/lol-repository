package com.mmrtr.lolrepository.domain.entity.event;

import com.mmrtr.lolrepository.riot.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BuildingEvents {

    private Long id;
    private int timelineTimestamp;

    private String assistingParticipantIds;
    private int bounty;
    private String buildingType;
    private int killerId;
    private String laneType;

    private int x;
    private int y;

    private int teamId;
    private long timestamp;
    private String towerType;
    private String type;

    public BuildingEvents(){};
    public BuildingEvents(int timelineTimestamp, EventsTimeLineDto eventsTimeLineDto) {

        this.timelineTimestamp = timelineTimestamp;
//        this.assistingParticipantIds = eventsTimeLineDto.getAssistingParticipantIds().stream().map( data -> data).toString();
        this.bounty = eventsTimeLineDto.getBounty();
        this.buildingType = eventsTimeLineDto.getBuildingType();
        this.killerId = eventsTimeLineDto.getKillerId();
        this.laneType = eventsTimeLineDto.getLaneType();
        this.x = eventsTimeLineDto.getPosition().getX();
        this.y = eventsTimeLineDto.getPosition().getX();
        this.teamId = eventsTimeLineDto.getTeamId();
        this.timestamp = eventsTimeLineDto.getTimestamp();
        this.towerType = eventsTimeLineDto.getTowerType();
        this.type = eventsTimeLineDto.getType();

    }
}
