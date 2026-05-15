package com.mmrtr.lol.infra.riot.interceptor;

import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class RetryInterceptor implements ClientHttpRequestInterceptor {

    static final int MAX_ATTEMPTS = 3;
    static final long INITIAL_BACKOFF_MS = 500L;
    static final double MULTIPLIER = 2.0;
    static final long MAX_BACKOFF_MS = 15_000L;
    static final double JITTER_RATIO = 0.25;

    private final MeterRegistry meterRegistry;
    private final Sleeper sleeper;

    public RetryInterceptor(MeterRegistry meterRegistry) {
        this(meterRegistry, Thread::sleep);
    }

    RetryInterceptor(MeterRegistry meterRegistry, Sleeper sleeper) {
        this.meterRegistry = meterRegistry;
        this.sleeper = sleeper;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String host = request.getURI().getHost();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                HttpStatusCode status = response.getStatusCode();

                recordResponse(status, host);

                if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                    Duration retryAfter = parseRetryAfter(response.getHeaders().getFirst("Retry-After"));
                    if (attempt == MAX_ATTEMPTS) {
                        closeQuietly(response);
                        recordExhausted();
                        throw new RiotRateLimitException(retryAfter, status, "Riot rate limit exhausted", LogLevel.WARN);
                    }
                    long base = retryAfter.isZero() ? exponentialBackoffMs(attempt) : retryAfter.toMillis();
                    log.warn("Riot 429 — attempt {}/{}, Retry-After={}s, sleep base={}ms",
                            attempt, MAX_ATTEMPTS, retryAfter.toSeconds(), base);
                    closeQuietly(response);
                    sleepWithBackoff(jitter(cap(base)));
                    continue;
                }

                if (status.is5xxServerError()) {
                    if (attempt == MAX_ATTEMPTS) {
                        recordExhausted();
                        return response;
                    }
                    log.warn("Riot 5xx — attempt {}/{}, status={}", attempt, MAX_ATTEMPTS, status.value());
                    closeQuietly(response);
                    sleepWithBackoff(jitter(cap(exponentialBackoffMs(attempt))));
                    continue;
                }

                if (attempt > 1) {
                    recordSuccessAfterRetry();
                }
                return response;
            } catch (IOException e) {
                if (attempt == MAX_ATTEMPTS) {
                    recordExhausted();
                    throw e;
                }
                log.warn("Riot IOException — attempt {}/{}: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                sleepWithBackoff(jitter(cap(exponentialBackoffMs(attempt))));
            }
        }
        throw new IllegalStateException("RetryInterceptor loop exited without resolution");
    }

    private Duration parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return Duration.ZERO;
        }
        try {
            long seconds = Long.parseLong(header.trim());
            return Duration.ofSeconds(Math.max(0L, seconds));
        } catch (NumberFormatException e) {
            log.debug("Failed to parse Retry-After header: {}", header);
            return Duration.ZERO;
        }
    }

    private long exponentialBackoffMs(int attempt) {
        return (long) (INITIAL_BACKOFF_MS * Math.pow(MULTIPLIER, attempt - 1));
    }

    private long cap(long ms) {
        return Math.min(ms, MAX_BACKOFF_MS);
    }

    private long jitter(long ms) {
        if (ms <= 0L) {
            return ms;
        }
        double delta = ms * JITTER_RATIO;
        double random = ThreadLocalRandom.current().nextDouble(-delta, delta);
        return Math.max(0L, (long) (ms + random));
    }

    private void sleepWithBackoff(long ms) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            if (ms > 0L) {
                sleeper.sleep(ms);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sample.stop(meterRegistry.timer("riot.api.retry.backoff"));
        }
    }

    private void recordResponse(HttpStatusCode status, String host) {
        meterRegistry.counter("riot.api.responses",
                "status", String.valueOf(status.value()),
                "host", host == null ? "unknown" : host).increment();
    }

    private void recordSuccessAfterRetry() {
        meterRegistry.counter("riot.api.retry.attempts", "outcome", "success").increment();
    }

    private void recordExhausted() {
        meterRegistry.counter("riot.api.retry.attempts", "outcome", "exhausted").increment();
    }

    private void closeQuietly(ClientHttpResponse response) {
        try {
            response.close();
        } catch (Exception ignored) {
            // close failure not actionable for retry control
        }
    }
}
