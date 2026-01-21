package com.mmrtr.lol.infra.rabbitmq.config;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ListenerRateLimiterConfig {

    private static final long GLOBAL_RATE_LIMIT = 20;

    @Bean
    public RRateLimiter globalApiRateLimiter(RedissonClient redissonClient) {
        RRateLimiter limiter = redissonClient.getRateLimiter("global:api:call:limiter");

        limiter.trySetRate(
                RateType.OVERALL,
                GLOBAL_RATE_LIMIT,
                Duration.ofSeconds(1)
        );

        return limiter;
    }


}
