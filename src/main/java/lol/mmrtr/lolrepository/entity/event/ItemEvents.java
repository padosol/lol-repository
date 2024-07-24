package lol.mmrtr.lolrepository.entity.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class ItemEvents {


    private Long id;
    private Long timeLineEventId;

    private int itemId;
    private int participantId;
    private long timestamp;
    private String type;

    private int afterId;
    private int beforeId;
    private int goldGain;
}
