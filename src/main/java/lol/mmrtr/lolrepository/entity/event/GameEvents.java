package lol.mmrtr.lolrepository.entity.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameEvents {

    private Long id;
    private Long timeLineEventId;

    private long timestamp;
    private long gameId;
    private long realTimestamp;
    private int winningTeam;
    private String type;

}
