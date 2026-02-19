package com.mmrtr.lol.domain.champion_stat.domain;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChampionSkillStat {

    private Long id;
    private int championId;
    private String teamPosition;
    private int season;
    private String tierGroup;
    private String platformId;
    private int queueId;
    private String gameVersion;
    private String skillOrder;
    private String skillPriority;
    private long games;
    private long wins;
}
