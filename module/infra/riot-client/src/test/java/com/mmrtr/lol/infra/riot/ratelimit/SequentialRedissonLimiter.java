package com.mmrtr.lol.infra.riot.ratelimit;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ④ 순차 · Redisson: 버킷마다 RRateLimiter(현재 RateLimitInterceptor 가 쓰는 라이브러리)를 두고
 * app.tryAcquire() → method.tryAcquire() 순으로 호출. 각 호출은 Redisson 내부 Lua 로 원자지만,
 * 두 호출 사이는 원자가 아니고 RRateLimiter 는 획득한 permit 을 돌려주는 API 가 없다.
 */
final class SequentialRedissonLimiter implements MultiBucketRateLimiter {

    private final RedissonClient redisson;
    private final Map<String, RRateLimiter> limiters = new ConcurrentHashMap<>();

    SequentialRedissonLimiter(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public void prepare(List<RateBucket> buckets) {
        for (RateBucket bucket : buckets) {
            limiter(bucket).trySetRate(RateType.OVERALL, bucket.limit(), bucket.window());
        }
    }

    @Override
    public boolean tryAcquire(List<RateBucket> buckets) {
        for (RateBucket bucket : buckets) {
            if (!limiter(bucket).tryAcquire()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public long consumed(RateBucket bucket) {
        return bucket.limit() - limiter(bucket).availablePermits();
    }

    private RRateLimiter limiter(RateBucket bucket) {
        return limiters.computeIfAbsent(bucket.key(), redisson::getRateLimiter);
    }
}
