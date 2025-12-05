package com.mmrtr.lolrepository.riot.dto.league;

import com.mmrtr.lolrepository.riot.dto.error.ErrorDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LeagueListDTO extends ErrorDTO {

    private	String leagueId;
    private List<LeagueItemDTO> entries;
    private	String tier;
    private	String name;
    private	String queue;



}
