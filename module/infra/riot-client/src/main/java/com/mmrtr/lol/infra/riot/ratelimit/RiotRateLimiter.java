package com.mmrtr.lol.infra.riot.ratelimit;

import com.mmrtr.lol.support.riot.RiotPermit;
import java.time.Duration;

/**
 * Riot 호출 한도 관리자의 응답 쪽 일. 허가는 {@link com.mmrtr.lol.support.riot.RiotPermitPort} 가 내주고,
 * 이 인터페이스는 riot-client 안({@code RiotCallRecorder})에서만 쓴다.
 */
public interface RiotRateLimiter {

    /** 허가된 permit 의 기록을 지금 시각(응답·실패 시각)으로 옮긴다. 기록이 없으면 아무것도 하지 않는다. */
    void complete(RiotPermit permit);

    /** 응답 헤더로 한도를 배운다. 빈 헤더·잘못된 헤더는 무시한다. */
    void learnLimits(String routing, String method, String appLimitHeader, String methodLimitHeader);

    /** 버킷을 정지한다. method 가 null 이면 routing 전체. */
    void pause(String routing, String methodOrNull, Duration duration);
}
