package com.mmrtr.lol.domain.champion_stat.domain;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChampionSpellStat {

    private Long id;
    private int championId;
    private String teamPosition;
    private int season;
    private String tierGroup;
    private String platformId;
    private int queueId;
    private String gameVersion;
    private int spell1Id;
    private int spell2Id;
    private long games;
    private long wins;
}
