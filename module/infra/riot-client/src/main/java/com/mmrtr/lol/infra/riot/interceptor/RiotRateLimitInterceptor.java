package com.mmrtr.lol.infra.riot.interceptor;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.infra.riot.ratelimit.RateLimitWindow;
import com.mmrtr.lol.infra.riot.ratelimit.RiotRateLimiter;
import com.mmrtr.lol.infra.riot.ratelimit.RiotRequestAttributes;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * 보내기 전 {@link RiotRateLimiter} 로 허가를 받고, 응답 헤더로 한도를 배우며 429 시 버킷을 정지한다.
 */
@Slf4j
public class RiotRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private static final String UNKNOWN = "unknown";
    private static final Duration NEAR_LIMIT_PAUSE = Duration.ofSeconds(1);

    private final RiotRateLimiter limiter;
    private final RiotAPIProperties.RateLimit props;
    private final MeterRegistry meterRegistry;
    private final Sleeper sleeper;
    private final LongSupplier clock;

    public RiotRateLimitInterceptor(RiotRateLimiter limiter, RiotAPIProperties properties,
                                    MeterRegistry meterRegistry) {
        this(limiter, properties, meterRegistry, Thread::sleep, System::currentTimeMillis);
    }

    RiotRateLimitInterceptor(RiotRateLimiter limiter, RiotAPIProperties properties,
                             MeterRegistry meterRegistry, Sleeper sleeper, LongSupplier clock) {
        this.limiter = limiter;
        this.props = properties.getRateLimit();
        this.meterRegistry = meterRegistry;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String routing = routingOf(request);
        RiotCallPriority priority = request.getAttributes().get(RiotRequestAttributes.PRIORITY)
                instanceof RiotCallPriority p ? p : RiotCallPriority.USER;
        String method = request.getAttributes().get(RiotRequestAttributes.METHOD) instanceof String m
                ? m : UNKNOWN;

        awaitPermit(routing, method, priority);

        ClientHttpResponse response = execution.execute(request, body);
        afterResponse(routing, method, response);
        return response;
    }

    private void awaitPermit(String routing, String method, RiotCallPriority priority) throws IOException {
        long start = clock.getAsLong();
        long maxWaitMs = maxWaitMs(priority);
        while (true) {
            long result = limiter.acquire(routing, method, priority);
            if (result == RiotRateLimiter.NO_CAPACITY) {
                throw rejected(routing, priority);
            }
            if (result >= 0L) {
                sleep(result);
                meterRegistry.timer("riot.ratelimit.wait", "routing", routing, "priority", priority.name())
                        .record(Duration.ofMillis(Math.max(0L, clock.getAsLong() - start)));
                return;
            }
            // YIELD: 양보 후 재시도. 누적 대기가 상한을 넘으면 포기한다.
            if (clock.getAsLong() - start >= maxWaitMs) {
                throw rejected(routing, priority);
            }
            sleep(props.getYieldBackoff().toMillis());
        }
    }

    private long maxWaitMs(RiotCallPriority priority) {
        return switch (priority) {
            case DEFERRED -> props.getDeferredMaxWait().toMillis();
            case USER, BACKGROUND -> props.getBackgroundMaxWait().toMillis();
        };
    }

    private RiotBudgetExceededException rejected(String routing, RiotCallPriority priority) {
        meterRegistry.counter("riot.ratelimit.rejected",
                "routing", routing, "priority", priority.name()).increment();
        return new RiotBudgetExceededException(routing, priority);
    }

    private void sleep(long ms) throws IOException {
        if (ms <= 0L) {
            return;
        }
        try {
            sleeper.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("RiotRateLimitInterceptor wait interrupted", e);
        }
    }

    private void afterResponse(String routing, String method, ClientHttpResponse response) throws IOException {
        HttpHeaders headers = response.getHeaders();
        String appLimit = headers.getFirst("X-App-Rate-Limit");
        String methodLimit = headers.getFirst("X-Method-Rate-Limit");
        // 응답은 이미 받았으므로 각 단계의 실패가 호출을 실패시키거나 다음 단계를 건너뛰게 하지 않는다.
        safely("learn", routing, method, () -> limiter.learnLimits(routing, method, appLimit, methodLimit));
        safely("near-limit", routing, method, () -> pauseIfNearLimit(routing, method, headers, appLimit, methodLimit));
        if (HttpStatus.TOO_MANY_REQUESTS.equals(response.getStatusCode())) {
            String type = headers.getFirst("X-Rate-Limit-Type");
            meterRegistry.counter("riot.api.rate_limited", "routing", routing, "type", typeTag(type)).increment();
            safely("429-pause", routing, method, () -> pauseOn429(routing, method, type, headers));
        }
    }

    private void safely(String step, String routing, String method, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Riot rate limit 후처리 실패 step={}, routing={}, method={}", step, routing, method, e);
        }
    }

    private void pauseIfNearLimit(String routing, String method, HttpHeaders headers,
                                  String appLimit, String methodLimit) {
        double ratio = props.getCountPauseRatio();
        if (nearLimit(appLimit, headers.getFirst("X-App-Rate-Limit-Count"), ratio)) {
            meterRegistry.counter("riot.ratelimit.near_limit", "routing", routing, "scope", "app").increment();
            limiter.pause(routing, null, NEAR_LIMIT_PAUSE);
        }
        if (nearLimit(methodLimit, headers.getFirst("X-Method-Rate-Limit-Count"), ratio)) {
            meterRegistry.counter("riot.ratelimit.near_limit", "routing", routing, "scope", "method").increment();
            limiter.pause(routing, method, NEAR_LIMIT_PAUSE);
        }
    }

    private void pauseOn429(String routing, String method, String type, HttpHeaders headers) {
        Duration pause = retryAfter(headers.getFirst("Retry-After"));
        if ("application".equalsIgnoreCase(type)) {
            limiter.pause(routing, null, pause);
        } else {
            limiter.pause(routing, method, pause);
        }
    }

    /** 429 의 X-Rate-Limit-Type 을 소문자 그대로, 없으면 none. */
    private static String typeTag(String type) {
        return type == null || type.isBlank() ? "none" : type.trim().toLowerCase(Locale.ROOT);
    }

    private boolean nearLimit(String limitHeader, String countHeader, double ratio) {
        if (limitHeader == null || countHeader == null) {
            return false;
        }
        return RateLimitWindow.nearLimit(
                RateLimitWindow.parse(limitHeader), RateLimitWindow.parse(countHeader), ratio);
    }

    private Duration retryAfter(String header) {
        if (header != null && !header.isBlank()) {
            try {
                long seconds = Long.parseLong(header.trim());
                if (seconds > 0L) {
                    return Duration.ofSeconds(seconds);
                }
            } catch (NumberFormatException e) {
                log.debug("Failed to parse Retry-After header: {}", header);
            }
        }
        return props.getDefaultPause();
    }

    private String routingOf(HttpRequest request) {
        String host = request.getURI().getHost();
        if (host == null) {
            return UNKNOWN;
        }
        int dot = host.indexOf('.');
        return dot < 0 ? host : host.substring(0, dot);
    }
}
