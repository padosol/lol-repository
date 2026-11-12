package com.mmrtr.lol.infra.riot.ratelimit;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

/** fixed-window 카운터 공통 연산. 윈도우는 첫 요청 시점에 시작한다 (Riot 의 윈도우 방식과 같음). */
final class RedisCounters {

    private RedisCounters() {
    }

    static RAtomicLong counter(RedissonClient redisson, RateBucket bucket) {
        return redisson.getAtomicLong(bucket.key());
    }

    /** INCR → 1 이면 PEXPIRE. (INCR 와 PEXPIRE 도 별개 왕복이라 그 사이에 죽으면 만료 없는 키가 남는다.) */
    static long incrementWithWindow(RedissonClient redisson, RateBucket bucket) {
        RAtomicLong counter = counter(redisson, bucket);
        long used = counter.incrementAndGet();
        if (used == 1) {
            counter.expire(bucket.window());
        }
        return used;
    }
}
