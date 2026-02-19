package com.mmrtr.lol.domain.champion_stat.domain;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChampionMatchupStat {

    private Long id;
    private int championId;
    private String teamPosition;
    private int season;
    private String tierGroup;
    private String platformId;
    private int queueId;
    private String gameVersion;
    private int opponentChampionId;
    private long games;
    private long wins;
    private BigDecimal avgKills;
    private BigDecimal avgDeaths;
    private BigDecimal avgAssists;
    private BigDecimal avgGoldDiff;
}
