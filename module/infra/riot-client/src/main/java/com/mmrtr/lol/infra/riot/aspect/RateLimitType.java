package com.mmrtr.lol.infra.riot.aspect;

import lombok.Getter;

import java.time.Duration;

@Getter
public enum RateLimitType {
    REGION_RATE_LIMITER("external:api:region", 460, Duration.ofSeconds(10), "regionRateLimiter"),
    PLATFORM_RATE_LIMITER("external:api:platform", 450, Duration.ofSeconds(10), "platformRateLimiter"),
    ;

    private final String key;
    private final int rateLimit;
    private final Duration duration;
    private final String beanName;

    RateLimitType(final String key, final int rateLimit, final Duration duration, final String beanName) {
        this.key = key;
        this.rateLimit = rateLimit;
        this.duration = duration;
        this.beanName = beanName;
    }
}
