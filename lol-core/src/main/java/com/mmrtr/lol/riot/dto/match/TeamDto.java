package com.mmrtr.lol.riot.dto.match;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TeamDto {

    private List<BanDto> bans;
    private ObjectivesDto objectives;
    private	int teamId;
    private	boolean win;

}
