package com.mmrtr.lol.riot.dto.champion_mastery;

import com.mmrtr.lol.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChampionMasteryDto extends ErrorDTO {

    private String puuid;
    private long championId;
    private int championLevel;
    private int championPoints;
    private long lastPlayTime;
    private long championPointsSinceLastLevel;
    private long championPointsUntilNextLevel;
    private boolean chestGranted;
    private int tokensEarned;
    private String summonerId;

}
