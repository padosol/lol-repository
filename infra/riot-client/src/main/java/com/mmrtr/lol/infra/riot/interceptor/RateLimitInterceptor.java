package com.mmrtr.lol.infra.riot.interceptor;

import com.mmrtr.lol.infra.riot.aspect.RateLimitType;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.ratelimit.HostRateLimitResolver;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final RedissonClient redissonClient;
    private final HostRateLimitResolver resolver;
    private final Map<String, RRateLimiter> rateLimiterCache = new ConcurrentHashMap<>();

    public RateLimitInterceptor(RedissonClient redissonClient, HostRateLimitResolver resolver) {
        this.redissonClient = redissonClient;
        this.resolver = resolver;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        RateLimitType type = resolver.resolve(request.getURI());
        RRateLimiter limiter = getOrCreateRateLimiter(type);

        if (!limiter.tryAcquire(1)) {
            log.warn("Rate limit exceeded for [{}]", type.getBeanName());
            throw new RiotClientException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit 초과", LogLevel.WARN);
        }

        return execution.execute(request, body);
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
