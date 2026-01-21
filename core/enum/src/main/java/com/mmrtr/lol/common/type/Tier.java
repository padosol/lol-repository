package com.mmrtr.lol.common.type;

import lombok.Getter;

@Getter
public enum Tier {

    CHALLENGER(10000),
    GRANDMASTER(9000),
    MASTER(8000),
    DIAMOND(7000),
    EMERALD(6000),
    PLATINUM(5000),
    GOLD(4000),
    SILVER(3000),
    BRONZE(2000),
    IRON(1000);

    private final int score;

    Tier(int score) {
        this.score = score;
    }
}
