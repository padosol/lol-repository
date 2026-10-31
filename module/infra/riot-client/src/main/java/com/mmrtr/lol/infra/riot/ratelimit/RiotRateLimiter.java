package com.mmrtr.lol.infra.riot.ratelimit;

import com.mmrtr.lol.support.riot.RiotCallPriority;
import java.time.Duration;

/**
 * Riot 호출 한도 관리자. (routing value) × (app | method) 버킷을 초 단위로 센다.
 */
public interface RiotRateLimiter {

    /** USER 가 예약 범위 안에서 빈 초를 찾지 못했다. */
    long NO_CAPACITY = -1L;

    /** DEFERRED·BACKGROUND 가 지금은 가져갈 수 없다. 잠시 뒤 다시 시도한다. */
    long YIELD = -2L;

    /**
     * 호출 한 건을 허가받는다.
     *
     * @return 보내기 전 기다릴 ms(0 이상), 또는 {@link #NO_CAPACITY} / {@link #YIELD}
     */
    long acquire(String routing, String method, RiotCallPriority priority);

    /** 응답 헤더로 한도를 배운다. 빈 헤더·잘못된 헤더는 무시한다. */
    void learnLimits(String routing, String method, String appLimitHeader, String methodLimitHeader);

    /** 버킷을 정지한다. method 가 null 이면 routing 전체. */
    void pause(String routing, String methodOrNull, Duration duration);

    /** USER 가 예약해 둔 가장 먼 초까지 남은 ms. 없으면 0. */
    long backlogMillis(String routing);

    /** 그 routing 의 app 한도로 계산한 초당 USER 허용 호출 수. */
    int userCallsPerSecond(String routing);
}
