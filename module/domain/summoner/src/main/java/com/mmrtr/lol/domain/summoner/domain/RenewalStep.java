package com.mmrtr.lol.domain.summoner.domain;

/** 갱신을 이루는 단계. 끝난 단계는 비트마스크로 레코드에 쌓는다. */
public enum RenewalStep {
    ACCOUNT(1),
    SUMMONER(2),
    LEAGUE(4),
    MATCH(8);

    /** 모든 단계가 끝났을 때의 비트마스크. */
    public static final int ALL_BITS = 15;

    private final int bit;

    RenewalStep(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }
}
