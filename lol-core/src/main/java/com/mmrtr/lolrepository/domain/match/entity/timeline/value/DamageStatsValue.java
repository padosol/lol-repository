package com.mmrtr.lolrepository.domain.match.entity.timeline.value;

import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class DamageStatsValue {
    private int magicDamageDone;
    private int magicDamageDoneToChampions;
    private int magicDamageTaken;
    private int physicalDamageDone;
    private int physicalDamageDoneToChampions;
    private int physicalDamageTaken;
    private int totalDamageDone;
    private int totalDamageDoneToChampions;
    private int totalDamageTaken;
    private int trueDamageDone;
    private int trueDamageDoneToChampions;
    private int trueDamageTaken;
}
