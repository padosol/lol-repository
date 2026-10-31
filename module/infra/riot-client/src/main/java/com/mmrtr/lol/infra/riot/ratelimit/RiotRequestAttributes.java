package com.mmrtr.lol.infra.riot.ratelimit;

/** RestClient 요청 attribute 키. 인터셉터가 우선순위·method 이름을 읽는다. */
public final class RiotRequestAttributes {

    public static final String PRIORITY = "riot.priority";
    public static final String METHOD = "riot.method";

    private RiotRequestAttributes() {
    }
}
