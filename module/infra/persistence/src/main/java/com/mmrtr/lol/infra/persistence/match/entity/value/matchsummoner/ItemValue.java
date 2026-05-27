package com.mmrtr.lol.infra.persistence.match.entity.value.matchsummoner;

import com.mmrtr.lol.domain.match.readmodel.ParticipantDto;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@Embeddable
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemValue {

    @Comment("아이템 슬롯 1")
    private int item0;
    @Comment("아이템 슬롯 2")
    private int item1;
    @Comment("아이템 슬롯 3")
    private int item2;
    @Comment("아이템 슬롯 4")
    private int item3;
    @Comment("아이템 슬롯 5")
    private int item4;
    @Comment("아이템 슬롯 6")
    private int item5;
    @Comment("아이템 슬롯 7")
    private int item6;
    @Comment("아이템 구매 횟수")
    private int itemsPurchased;
    @Comment("소모품 구매 횟수")
    private int consumablesPurchased;

    public ItemValue(ParticipantDto participantDto) {
        this.item0 = participantDto.getItem0();
        this.item1 = participantDto.getItem1();
        this.item2 = participantDto.getItem2();
        this.item3 = participantDto.getItem3();
        this.item4 = participantDto.getItem4();
        this.item5 = participantDto.getItem5();
        this.item6 = participantDto.getItem6();
        this.itemsPurchased = participantDto.getItemsPurchased();
        this.consumablesPurchased = participantDto.getConsumablesPurchased();
    }
}
