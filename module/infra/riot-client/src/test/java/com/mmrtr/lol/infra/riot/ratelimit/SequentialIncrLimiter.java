package com.mmrtr.lol.infra.riot.ratelimit;

import org.redisson.api.RedissonClient;

import java.util.List;

/**
 * ② 순차 · 키별 원자: app INCR → 한도 초과면 거절, 아니면 method INCR → 한도 초과면 거절.
 * 키마다 INCR 은 원자라 한도를 넘겨 허용하지는 않지만, method 에서 거절돼도 app 에서 쓴 슬롯은 되돌리지 않는다.
 */
final class SequentialIncrLimiter implements MultiBucketRateLimiter {

    private final RedissonClient redisson;

    SequentialIncrLimiter(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public boolean tryAcquire(List<RateBucket> buckets) {
        for (RateBucket bucket : buckets) {
            if (RedisCounters.incrementWithWindow(redisson, bucket) > bucket.limit()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public long consumed(RateBucket bucket) {
        // 거절된 요청도 INCR 하므로 카운터는 한도를 넘어 계속 오른다 — 실제로 막힌 슬롯 수는 한도까지.
        return Math.min(RedisCounters.counter(redisson, bucket).get(), bucket.limit());
    }
}
