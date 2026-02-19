package com.mmrtr.lol.domain.champion_stat.domain;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChampionRuneStat {

    private Long id;
    private int championId;
    private String teamPosition;
    private int season;
    private String tierGroup;
    private String platformId;
    private int queueId;
    private String gameVersion;
    private int primaryRuneId;
    private String primaryRuneIds;
    private int secondaryRuneId;
    private String secondaryRuneIds;
    private int statOffense;
    private int statFlex;
    private int statDefense;
    private long games;
    private long wins;
}
