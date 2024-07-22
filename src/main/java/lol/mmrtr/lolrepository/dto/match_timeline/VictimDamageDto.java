package lol.mmrtr.lolrepository.dto.match_timeline;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VictimDamageDto {
    private boolean basic;
    private int magicDamage;
    private String name;
    private int participantId;
    private int physicalDamage;
    private String spellName;
    private int spellSlot;
    private int trueDamage;
    private String type;
}
