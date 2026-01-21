package com.mmrtr.lol.infra.riot.dto.match_timeline;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DamageStatsDto {
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
