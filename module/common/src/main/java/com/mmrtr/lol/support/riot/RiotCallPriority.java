package com.mmrtr.lol.support.riot;

/**
 * Riot API 호출 우선순위. level 이 낮을수록 먼저 처리된다.
 */
public enum RiotCallPriority {
    USER(1),
    DEFERRED(2),
    BACKGROUND(3);

    private final int level;

    RiotCallPriority(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }
}
