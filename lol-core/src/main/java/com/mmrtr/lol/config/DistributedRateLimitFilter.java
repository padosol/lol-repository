package com.mmrtr.lol.config;

import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@Component
public class DistributedRateLimitFilter implements ExchangeFilterFunction {

    private final RRateLimiter rateLimiter;
    private static final String LIMITER_KEY = "external:api:global_limit";

    public DistributedRateLimitFilter(RedissonClient redissonClient) {
        this.rateLimiter = redissonClient.getRateLimiter(LIMITER_KEY);
        
        // 10초당 490회 제한
        this.rateLimiter.trySetRate(RateType.OVERALL, 490, 10, RateIntervalUnit.SECONDS);
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {

        if (rateLimiter.tryAcquire(1)) {
            return next.exchange(request);
        }

        return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .body("Rate limit exceeded for external API.")
                .build());
    }
}
