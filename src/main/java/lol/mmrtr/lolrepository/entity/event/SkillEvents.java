package lol.mmrtr.lolrepository.entity.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SkillEvents {

    private Long id;
    private Long timeLineEventId;

    private int skillSlot;
    private int participantId;
    private String levelUpType;
    private long timestamp;
    private String type;
}
