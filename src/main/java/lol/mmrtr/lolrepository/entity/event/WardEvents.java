package lol.mmrtr.lolrepository.entity.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WardEvents {

    private Long id;

    private Long timeLineEventId;

    private int participantId;
    private String wardType;

    private long timestamp;
    private String type;
}
