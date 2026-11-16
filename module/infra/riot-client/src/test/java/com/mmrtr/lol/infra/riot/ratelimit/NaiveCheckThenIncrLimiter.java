package com.mmrtr.lol.infra.riot.ratelimit;

import org.redisson.api.RedissonClient;

import java.util.List;

/**
 * ① 순차 · 비원자: app GET → method GET 으로 검사한 뒤, 통과하면 app INCR → method INCR.
 * 검사와 증가가 분리돼 있어 그 사이에 다른 요청이 같은 "여유 있음"을 보고 함께 통과할 수 있다.
 */
final class NaiveCheckThenIncrLimiter implements MultiBucketRateLimiter {

    private final RedissonClient redisson;
    private final Runnable afterCheck;

    NaiveCheckThenIncrLimiter(RedissonClient redisson) {
        this(redisson, () -> { });
    }

    /** afterCheck: 검사와 증가 사이에 끼우는 훅 (테스트에서 경합을 결정적으로 재현할 때 사용). */
    NaiveCheckThenIncrLimiter(RedissonClient redisson, Runnable afterCheck) {
        this.redisson = redisson;
        this.afterCheck = afterCheck;
    }

    @Override
    public boolean tryAcquire(List<RateBucket> buckets) {
        for (RateBucket bucket : buckets) {
            if (RedisCounters.counter(redisson, bucket).get() >= bucket.limit()) {
                return false;
            }
        }
        afterCheck.run();
        for (RateBucket bucket : buckets) {
            RedisCounters.incrementWithWindow(redisson, bucket);
        }
        return true;
    }

    @Override
    public long consumed(RateBucket bucket) {
        return RedisCounters.counter(redisson, bucket).get();
    }
}
