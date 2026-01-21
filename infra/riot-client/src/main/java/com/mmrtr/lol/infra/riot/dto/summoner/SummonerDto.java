package com.mmrtr.lol.infra.riot.dto.summoner;

import com.mmrtr.lol.infra.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SummonerDto extends ErrorDTO {

    private int profileIconId;
    private long revisionDate;
    private String puuid;
    private long summonerLevel;

}
