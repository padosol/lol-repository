package lol.mmrtr.lolrepository.entity.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LevelEvents {

    private Long id;
    private Long timeLineEventId;

    private int level;
    private int participantId;
    private long timestamp;
    private String type;
}
