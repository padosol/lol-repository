package lol.mmrtr.lolrepository.entity;

import lol.mmrtr.lolrepository.dto.match_timeline.EventsTimeLineDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TimeLineEvent {

    private String matchId;
    private int timestamp;
    private long eventTimestamp;

    private long realTimestamp;
    private String type;
    private int itemId;

    private int participantId;
    private String puuid;

    private String levelUpType;
    private int skillSlot;
    private int creatorId;
    private String wardType;
    private int level;

    private String assistingParticipantIds;

    private int bounty;
    private int killStreakLength;
    private int killerId;

    private int x;
    private int y;

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


    public TimeLineEvent(){};

    public TimeLineEvent(
            String matchId,
            int frameTimestamp,
            String puuid,
            EventsTimeLineDto eventsTimeLineDto
    ) {
        this.matchId = matchId;
        this.timestamp = frameTimestamp;
        this.eventTimestamp = eventsTimeLineDto.getTimestamp();
        this.realTimestamp = eventsTimeLineDto.getRealTimestamp();
        this.type = eventsTimeLineDto.getType();
        this.itemId = eventsTimeLineDto.getItemId();
        this.participantId = eventsTimeLineDto.getParticipantId();
        this.puuid = puuid;
        this.levelUpType = eventsTimeLineDto.getLevelUpType();
        this.skillSlot = eventsTimeLineDto.getSkillSlot();
        this.creatorId = eventsTimeLineDto.getCreatorId();
        this.wardType = eventsTimeLineDto.getWardType();
        this.level = eventsTimeLineDto.getLevel();

        StringBuffer sb = new StringBuffer();
        if(eventsTimeLineDto.getAssistingParticipantIds() != null) {
            for (Integer assistingParticipantId : eventsTimeLineDto.getAssistingParticipantIds()) {
                if(!sb.isEmpty()) {
                    sb.append(",");
                }
                sb.append(assistingParticipantId);
            }
        }

        this.assistingParticipantIds = sb.toString();
        this.bounty = eventsTimeLineDto.getBounty();
        this.killStreakLength = eventsTimeLineDto.getKillStreakLength();
        this.killerId = eventsTimeLineDto.getKillerId();

        if(eventsTimeLineDto.getPosition() != null) {
            this.x = eventsTimeLineDto.getPosition().getX();
            this.y = eventsTimeLineDto.getPosition().getY();
        }

        this.victimId = eventsTimeLineDto.getVictimId();
        this.killType = eventsTimeLineDto.getKillType();
        this.laneType = eventsTimeLineDto.getLaneType();
        this.teamId = eventsTimeLineDto.getTeamId();
        this.multiKillLength = eventsTimeLineDto.getMultiKillLength();
        this.killerTeamId = eventsTimeLineDto.getKillerTeamId();
        this.monsterType = eventsTimeLineDto.getMonsterType();
        this.monsterSubType = eventsTimeLineDto.getMonsterSubType();
        this.buildingType = eventsTimeLineDto.getBuildingType();
        this.towerType = eventsTimeLineDto.getTowerType();
        this.afterId = eventsTimeLineDto.getAfterId();
        this.beforeId = eventsTimeLineDto.getBeforeId();
        this.goldGain = eventsTimeLineDto.getGoldGain();
        this.gameId = eventsTimeLineDto.getGameId();
        this.winningTeam = eventsTimeLineDto.getWinningTeam();
        this.transformType = eventsTimeLineDto.getTransformType();
        this.name = eventsTimeLineDto.getName();
        this.shutdownBounty = eventsTimeLineDto.getShutdownBounty();
        this.actualStartTime = eventsTimeLineDto.getActualStartTime();

    }
}
