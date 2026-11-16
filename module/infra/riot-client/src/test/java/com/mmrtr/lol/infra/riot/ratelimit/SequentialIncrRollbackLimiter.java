package com.mmrtr.lol.infra.riot.ratelimit;

import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;

/**
 * ③ 순차 + 보상: ② 와 같지만, 어느 버킷에서 거절되면 그때까지 INCR 한 버킷을 모두 DECR 로 되돌린다.
 * 최종 카운트는 맞춰지지만, INCR~DECR 사이에는 카운터가 부풀어 있어 다른 요청이 잘못 거절될 수 있다.
 */
final class SequentialIncrRollbackLimiter implements MultiBucketRateLimiter {

    private final RedissonClient redisson;
    private final Runnable beforeRollback;

    SequentialIncrRollbackLimiter(RedissonClient redisson) {
        this(redisson, () -> { });
    }

    /** beforeRollback: 초과 감지 후 DECR 직전에 끼우는 훅 (테스트에서 보상 지연을 재현할 때 사용). */
    SequentialIncrRollbackLimiter(RedissonClient redisson, Runnable beforeRollback) {
        this.redisson = redisson;
        this.beforeRollback = beforeRollback;
    }

    @Override
    public boolean tryAcquire(List<RateBucket> buckets) {
        List<RateBucket> taken = new ArrayList<>(buckets.size());
        for (RateBucket bucket : buckets) {
            long used = RedisCounters.incrementWithWindow(redisson, bucket);
            taken.add(bucket);
            if (used > bucket.limit()) {
                beforeRollback.run();
                for (RateBucket t : taken) {
                    RedisCounters.counter(redisson, t).decrementAndGet();
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public long consumed(RateBucket bucket) {
        return RedisCounters.counter(redisson, bucket).get();
    }
}
