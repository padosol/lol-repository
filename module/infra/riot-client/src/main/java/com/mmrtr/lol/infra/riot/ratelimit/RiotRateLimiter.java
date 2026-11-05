package com.mmrtr.lol.infra.riot.ratelimit;

import com.mmrtr.lol.support.riot.RiotCallPriority;
import java.time.Duration;

/**
 * Riot 호출 한도 관리자. (routing value) × (app | method) 버킷의 한도 창마다 슬라이딩 창으로 센다.
 */
public interface RiotRateLimiter {

    /**
     * 호출 한 건을 허가받는다. 거절해도 기록하지 않으므로 같은 eventId 로 다시 물어도 된다.
     *
     * @return 0 = 지금 보내도 된다(eventId 로 기록함). 0 보다 크면 그만큼 ms 뒤 다시 물어야 한다(기록 안 함).
     */
    long acquire(String eventId, String routing, String method, RiotCallPriority priority);

    /** 허가된 호출의 기록을 지금 시각(응답·실패 시각)으로 옮긴다. 기록이 없으면 아무것도 하지 않는다. */
    void complete(String eventId, String routing, String method, RiotCallPriority priority);

    /** 응답 헤더로 한도를 배운다. 빈 헤더·잘못된 헤더는 무시한다. */
    void learnLimits(String routing, String method, String appLimitHeader, String methodLimitHeader);

    /** 버킷을 정지한다. method 가 null 이면 routing 전체. */
    void pause(String routing, String methodOrNull, Duration duration);
}
