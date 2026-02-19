package com.mmrtr.lol.domain.champion_stat.domain;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChampionItemStat {

    private Long id;
    private int championId;
    private String teamPosition;
    private int season;
    private String tierGroup;
    private String platformId;
    private int queueId;
    private String gameVersion;
    private String buildType;
    private String itemIds;
    private long games;
    private long wins;
}
