package com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner;


import com.mmrtr.lol.infra.riot.dto.match.ParticipantDto;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Embeddable
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatValue {

    @Comment("방어 룬 스탯")
    private int defense;
    @Comment("유연 룬 스탯")
    private int flex;
    @Comment("공격 룬 스탯")
    private int offense;

    public StatValue(ParticipantDto participantDto) {
        this.defense = participantDto.getPerks().getStatPerks().getDefense();
        this.flex = participantDto.getPerks().getStatPerks().getFlex();
        this.offense = participantDto.getPerks().getStatPerks().getOffense();
    }
}
