package com.mmrtr.lol.infra.riot.ratelimit;

import org.redisson.api.RedissonClient;

import java.util.function.Function;

/** 비교 대상 전략. 모든 전략은 app 버킷 → method 버킷 순으로 검사한다. */
enum RateLimitStrategy {

    NAIVE_CHECK_THEN_INCR("① 순차 GET 검사 → INCR (비원자)", "거절 1~2 / 허용 4~6",
            NaiveCheckThenIncrLimiter::new),
    SEQUENTIAL_INCR("② 순차 app INCR → method INCR", "1~2 (+PEXPIRE)",
            SequentialIncrLimiter::new),
    SEQUENTIAL_INCR_ROLLBACK("③ 순차 INCR + 보상 DECR", "2~4 (+PEXPIRE)",
            SequentialIncrRollbackLimiter::new),
    SEQUENTIAL_REDISSON("④ 순차 Redisson RRateLimiter ×2 (현행 라이브러리)", "1~2 (각각 Lua)",
            SequentialRedissonLimiter::new),
    LUA_ATOMIC("⑤ Lua 한 번에 (app+method)", "1",
            LuaAtomicLimiter::new);

    private final String label;
    private final String roundTrips;
    private final Function<RedissonClient, MultiBucketRateLimiter> factory;

    RateLimitStrategy(String label, String roundTrips,
            Function<RedissonClient, MultiBucketRateLimiter> factory) {
        this.label = label;
        this.roundTrips = roundTrips;
        this.factory = factory;
    }

    String label() {
        return label;
    }

    /** 요청 1건당 Redis 왕복 수. */
    String roundTrips() {
        return roundTrips;
    }

    MultiBucketRateLimiter create(RedissonClient redisson) {
        return factory.apply(redisson);
    }
}
