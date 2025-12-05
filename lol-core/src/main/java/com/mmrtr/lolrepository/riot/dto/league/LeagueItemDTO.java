package com.mmrtr.lolrepository.riot.dto.league;

import com.mmrtr.lolrepository.riot.dto.error.ErrorDTO;
import lombok.Getter;

@Getter
public class LeagueItemDTO extends ErrorDTO {

    private	boolean freshBlood;
    private	int wins;
    private	boolean inactive;
    private	boolean veteran;
    private	boolean hotStreak;
    private	String rank;
    private	int leaguePoints;
    private	int losses;
    private	String summonerId;

}
