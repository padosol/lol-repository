package com.mmrtr.lol.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    private static final String REDIS_PROTOCOL_PREFIX = "redis://";

    private static final String LIMITER_KEY = "external:api:global_limit";

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(REDIS_PROTOCOL_PREFIX + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }

    @Bean
    public RRateLimiter rateLimiter(RedissonClient redissonClient) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(LIMITER_KEY);

        // 10초당 490회 제한
        rateLimiter.trySetRate(RateType.OVERALL, 490, Duration.ofSeconds(10));

        return rateLimiter;
    };

}