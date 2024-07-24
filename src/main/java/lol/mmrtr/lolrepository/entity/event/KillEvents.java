package lol.mmrtr.lolrepository.entity.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KillEvents {

    private Long id;
    private Long timeLineEventId;

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
}
