package com.mmrtr.lol.infra.riot.dto.league;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeagueItemDto {

    private String puuid;
    private String summonerId;
    private String summonerName;
    private int leaguePoints;
    private String rank;
    private int wins;
    private int losses;
    private boolean veteran;
    private boolean inactive;
    private boolean freshBlood;
    private boolean hotStreak;
}
