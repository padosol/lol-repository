package lol.mmrtr.lolrepository.domain.match.entity.value.matchsummoner;


import jakarta.persistence.Embeddable;
import lol.mmrtr.lolrepository.riot.dto.match.ParticipantDto;
import lombok.*;

@Getter
@Setter
@Embeddable
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatValue {

    private int defense;
    private int flex;
    private int offense;

    public StatValue(ParticipantDto participantDto) {
        this.defense = participantDto.getPerks().getStatPerks().getDefense();
        this.flex = participantDto.getPerks().getStatPerks().getFlex();
        this.offense = participantDto.getPerks().getStatPerks().getOffense();
    }
}
