package com.mmrtr.lol.common.type;

import lombok.Getter;

@Getter
public enum Division {

    I(100),
    II(200),
    III(300),
    IV(400);

    private final int score;

    Division(int score) {
        this.score = score;
    }
}
