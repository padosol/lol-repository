package com.mmrtr.lol.infra.riot.dto.match_timeline;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EventsTimeLineDto {
    private long timestamp;
    private String type;
    private int participantId;
    private int itemId;
    private int afterId;
    private int beforeId;
    private int goldGain;
    private int skillSlot;
    private String levelUpType;
    private String wardType;
    private int creatorId;
    private int killerId;
    private PositionDto position;
    private int victimId;
    private List<Integer> assistingParticipantIds;
    private int bounty;
    private int killStreakLength;
    private String killType;
    private List<VictimDamageDto> victimDamageDealt;
    private List<VictimDamageDto> victimDamageReceived;
    private String buildingType;
    private String laneType;
    private String towerType;
    private int teamId;
    private String monsterType;
    private String monsterSubType;
    private int killerTeamId;
}
