package com.mmrtr.lol.infra.riot.dto.league;

import com.mmrtr.lol.infra.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LeagueListDto extends ErrorDTO {

    private String tier;
    private String leagueId;
    private String queue;
    private String name;
    private List<LeagueItemDto> entries;
}
