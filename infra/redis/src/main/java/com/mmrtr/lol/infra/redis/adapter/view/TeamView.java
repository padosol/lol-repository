package com.mmrtr.lol.infra.redis.adapter.view;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamView {
    private TeamInfoView blueTeam;
    private TeamInfoView redTeam;
}
