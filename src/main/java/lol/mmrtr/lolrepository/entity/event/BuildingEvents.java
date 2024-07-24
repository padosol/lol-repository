package lol.mmrtr.lolrepository.entity.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BuildingEvents {

    private Long id;
    private Long timeLineEventId;

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
}
