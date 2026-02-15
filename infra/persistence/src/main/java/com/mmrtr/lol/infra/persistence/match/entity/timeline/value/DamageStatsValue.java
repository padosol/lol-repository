package com.mmrtr.lol.infra.persistence.match.entity.timeline.value;

import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.Comment;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class DamageStatsValue {
    @Comment("마법 피해량")
    private int magicDamageDone;
    @Comment("챔피언에게 가한 마법 피해량")
    private int magicDamageDoneToChampions;
    @Comment("받은 마법 피해량")
    private int magicDamageTaken;
    @Comment("물리 피해량")
    private int physicalDamageDone;
    @Comment("챔피언에게 가한 물리 피해량")
    private int physicalDamageDoneToChampions;
    @Comment("받은 물리 피해량")
    private int physicalDamageTaken;
    @Comment("총 피해량")
    private int totalDamageDone;
    @Comment("챔피언에게 가한 총 피해량")
    private int totalDamageDoneToChampions;
    @Comment("받은 총 피해량")
    private int totalDamageTaken;
    @Comment("고정 피해량")
    private int trueDamageDone;
    @Comment("챔피언에게 가한 고정 피해량")
    private int trueDamageDoneToChampions;
    @Comment("받은 고정 피해량")
    private int trueDamageTaken;
}
