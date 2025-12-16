package com.mmrtr.lol.riot.type;

import lombok.Getter;

@Getter
public enum Division {

    I(100),
    II(200),
    III(300),
    IV(400);

    private int score;

    Division(int score) {
        this.score = score;
    }
}
