package com.mmrtr.lol.infra.riot.ratelimit;

import java.time.Duration;

/**
 * Riot 호출 한도 관리자의 응답 쪽 일. 429 정지만 한다. 허가는 {@link com.mmrtr.lol.support.riot.RiotPermitPort} 가 내주고,
 * 이 인터페이스는 riot-client 안({@code RiotCallRecorder})에서만 쓴다.
 */
public interface RiotRateLimiter {

    /** 버킷을 정지한다. method 가 null 이면 routing 전체. */
    void pause(String routing, String methodOrNull, Duration duration);
}
