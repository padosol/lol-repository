package lol.mmrtr.lolrepository.entity.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChampionSpecialKillEvent {

    private Long id;
    private Long timeLineEventId;

    private String killType;
    private int killerId;
    private int multiKillLength;

    private int x;
    private int y;

    private long timestamp;
    private String type;
}
