package com.mmrtr.lol.infra.riot.ratelimit;

import java.time.Duration;

/**
 * Riot 호출 한도 관리자의 응답 쪽 일. 429 정지와, Riot 이 알려 준 한도·사용량을 비교용으로 남기는 일을 한다. 허가는
 * {@link com.mmrtr.lol.support.riot.RiotPermitPort} 가 내주고, 이 인터페이스는 riot-client 안({@code RiotCallRecorder})에서만
 * 쓴다.
 */
public interface RiotRateLimiter {

    /** 버킷을 정지한다. method 가 null 이면 routing 전체. */
    void pause(String routing, String methodOrNull, Duration duration);

    /** 응답의 한도 헤더를 routing 의 app 버킷과 그 method 버킷에 덮어쓴다. 허가 상한은 바꾸지 않는다 (지표로 비교만 한다). */
    void observe(String routing, String method, RiotLimitHeaders headers);
}
