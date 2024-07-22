package lol.mmrtr.lolrepository.dto.match_timeline;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EventsTimeLineDto {

    private long timestamp;
    private long realTimestamp;
    private String type;

    //
    private int itemId;
    private int participantId;
    private String levelUpType;
    private int skillSlot;
    private int creatorId;
    private String wardType;
    private int level;
    private List<Integer> assistingParticipantIds;
    private int bounty;
    private int killStreakLength;
    private int killerId;
    private PositionDto position;
    private List<VictimDamageDto> victimDamageDealt;
    private List<VictimDamageDto> victimDamageReceived;
    private int victimId;
    private String killType;
    private String laneType;
    private int teamId;
    private int multiKillLength;
    private int killerTeamId;
    private String monsterType;
    private String monsterSubType;
    private String buildingType;
    private String towerType;
    private int afterId;
    private int beforeId;
    private int goldGain;
    private long gameId;
    private int winningTeam;
    private String transformType;
    private String name;
    private int shutdownBounty;
    private int actualStartTime;

}
