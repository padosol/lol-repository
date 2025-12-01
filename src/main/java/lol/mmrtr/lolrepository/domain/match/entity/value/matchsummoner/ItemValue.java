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
public class ItemValue {

    private int item0;
    private int item1;
    private int item2;
    private int item3;
    private int item4;
    private int item5;
    private int item6;

    public ItemValue(ParticipantDto participantDto) {
        this.item0 = participantDto.getItem0();
        this.item1 = participantDto.getItem1();
        this.item2 = participantDto.getItem2();
        this.item3 = participantDto.getItem3();
        this.item4 = participantDto.getItem4();
        this.item5 = participantDto.getItem5();
        this.item6 = participantDto.getItem6();
    }

}
