package com.mmrtr.lol.domain.league.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SummonerRanking {
    private Long id;
    private String puuid;
    private String queue;

    private int currentRank;
    private int previousRank;
    private int rankChange;

    private String gameName;
    private String tagLine;

    private String mostChampion1;
    private String mostChampion2;
    private String mostChampion3;

    private int wins;
    private int losses;
    private BigDecimal winRate;

    private String tier;
    private String rank;
    private int leaguePoints;

    private LocalDateTime snapshotAt;
}
