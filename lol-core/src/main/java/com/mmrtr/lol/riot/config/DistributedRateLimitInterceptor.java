package com.mmrtr.lol.riot.config;

import java.io.IOException;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DistributedRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final RRateLimiter rateLimiter;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (rateLimiter.tryAcquire(1)) {
            return execution.execute(request, body);
        }

        return new RateLimitHttpResponse("Rate limit exceeded for external API.".getBytes(), HttpStatus.TOO_MANY_REQUESTS);
    }
}
