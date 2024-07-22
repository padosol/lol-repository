package lol.mmrtr.lolrepository.dto.match_timeline;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticipantFrameDto {

    private ChampionStatsDto championStats;
    private int currentGold;
    private DamageStatsDto damageStats;
    private int goldPerSecond;
    private int jungleMinionsKilled;
    private int level;
    private int minionsKilled;
    private int participantId;
    private PositionDto position;
    private int timeEnemySpentControlled;
    private int totalGold;
    private int xp;
}