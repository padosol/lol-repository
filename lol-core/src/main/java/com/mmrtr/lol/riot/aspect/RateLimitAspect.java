package com.mmrtr.lol.riot.aspect;

import com.mmrtr.lol.riot.exception.RiotClientException;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
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

        // 1. 해당 Enum 타입에 맞는 RRateLimiter 인스턴스를 가져오거나 최초 생성합니다.
        RRateLimiter limiter = getOrCreateRateLimiter(type);

        // 2. 토큰 획득 시도 및 로직 실행
        if (limiter.tryAcquire(1)) {
            return joinPoint.proceed();
        }

        throw new RiotClientException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다.", LogLevel.WARN);
    }

    private RRateLimiter getOrCreateRateLimiter(RateLimitType type) {

        // computeIfAbsent: 맵에 키가 없으면 람다식을 실행하여 값을 생성하고 저장 후 반환합니다. (스레드 안전)
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
