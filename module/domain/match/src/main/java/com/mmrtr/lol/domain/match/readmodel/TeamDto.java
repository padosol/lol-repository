package com.mmrtr.lol.domain.match.readmodel;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TeamDto {
    private List<BanDto> bans;
    private FeatsDto feats;
    private ObjectivesDto objectives;
    private int teamId;
    private boolean win;
}
