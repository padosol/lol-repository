package com.mmrtr.lol.infra.redis.adapter.view;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TeamInfoView {
    private int teamId;
    private boolean win;
    private int championKills;
    private int baronKills;
    private int dragonKills;
    private int towerKills;
    private int inhibitorKills;
}
