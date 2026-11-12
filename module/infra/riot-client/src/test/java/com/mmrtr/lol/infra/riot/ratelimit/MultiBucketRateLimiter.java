package com.mmrtr.lol.infra.riot.ratelimit;

import java.util.List;

/**
 * 한 요청이 통과해야 하는 여러 버킷(app → method 순)을 검사하는 rate limiter.
 * 비교 실험용 — 전략마다 Redis 호출 방식(순차 / 보상 / Lua)이 다르다.
 */
interface MultiBucketRateLimiter {

    /** 실험 시작 전 버킷 설정(Redisson RRateLimiter 의 trySetRate 등). 기본은 아무것도 하지 않는다. */
    default void prepare(List<RateBucket> buckets) {
    }

    /** 모든 버킷에 여유가 있으면 각 버킷에서 1 슬롯씩 쓰고 true. 하나라도 차 있으면 false. */
    boolean tryAcquire(List<RateBucket> buckets);

    /** 이 버킷에서 이번 윈도우에 더 이상 다른 요청에게 줄 수 없는(이미 쓴) 슬롯 수. */
    long consumed(RateBucket bucket);
}
