package com.mmrtr.lol.infra.riot.aspect;

import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private final RedissonClient redissonClient;
    private final Map<String, RRateLimiter> rateLimiterCache = new ConcurrentHashMap<>();

    public RateLimitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(rateLimited)")
    public Object applyRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {

        RateLimitType type = rateLimited.type();

        RRateLimiter limiter = getOrCreateRateLimiter(type);

        if (limiter.tryAcquire(1)) {
            return joinPoint.proceed();
        }

        throw new RiotClientException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다.", LogLevel.WARN);
    }

    private RRateLimiter getOrCreateRateLimiter(RateLimitType type) {

        return rateLimiterCache.computeIfAbsent(type.getBeanName(), k -> {
            log.debug("RateLimiter Lazy Load: {}", type.getBeanName());
            RRateLimiter limiter = redissonClient.getRateLimiter(type.getKey());

            limiter.trySetRate(
                    RateType.OVERALL,
                    type.getRateLimit(),
                    type.getDuration()
            );

            return limiter;
        });
    }
}
