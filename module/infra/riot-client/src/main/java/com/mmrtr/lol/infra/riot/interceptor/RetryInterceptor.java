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

                if (HttpStatus.TOO_MANY_REQUESTS.equals(status)) {
                    Duration retryAfter = parseRetryAfter(response.getHeaders().getFirst("Retry-After"));
                    if (attempt == MAX_ATTEMPTS) {
                        closeQuietly(response);
                        recordExhausted();
                        throw new RiotRateLimitException(retryAfter, status, "Riot rate limit exhausted", LogLevel.WARN);
                    }
                    closeQuietly(response);
                    long sleepMs = computeBackoffMs(attempt, retryAfter);
                    log.warn("Riot 429 — attempt {}/{}, Retry-After={}s, sleep={}ms",
                            attempt, MAX_ATTEMPTS, retryAfter.toSeconds(), sleepMs);
                    sleepOrAbort(sleepMs);
                    continue;
                }

                if (status.is5xxServerError()) {
                    if (attempt == MAX_ATTEMPTS) {
                        recordExhausted();
                        return response;
                    }
                    closeQuietly(response);
                    long sleepMs = computeBackoffMs(attempt, null);
                    log.warn("Riot 5xx — attempt {}/{}, status={}, sleep={}ms",
                            attempt, MAX_ATTEMPTS, status.value(), sleepMs);
                    sleepOrAbort(sleepMs);
                    continue;
                }

                if (attempt > 1) {
                    recordSuccessAfterRetry();
                }
                return response;
            } catch (RiotRateLimitException e) {
                if (attempt == MAX_ATTEMPTS) {
                    recordExhausted();
                    throw e;
                }
                long sleepMs = computeBackoffMs(attempt, e.getRetryAfter());
                log.warn("Riot RiotRateLimitException — attempt {}/{}, sleep={}ms",
                        attempt, MAX_ATTEMPTS, sleepMs);
                sleepOrAbort(sleepMs);
            } catch (IOException e) {
                if (e.getCause() instanceof InterruptedException) {
                    throw e;
                }
                if (attempt == MAX_ATTEMPTS) {
                    recordExhausted();
                    throw e;
                }
                long sleepMs = computeBackoffMs(attempt, null);
                log.warn("Riot IOException — attempt {}/{}, sleep={}ms: {}",
                        attempt, MAX_ATTEMPTS, sleepMs, e.getMessage());
                sleepOrAbort(sleepMs);
            }
        }
        throw new IllegalStateException("RetryInterceptor loop exited without resolution");
    }

    private long computeBackoffMs(int attempt, Duration retryAfter) {
        long base = (retryAfter == null || retryAfter.isZero())
                ? exponentialBackoffMs(attempt)
                : retryAfter.toMillis();
        return jitter(cap(base));
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

    private void sleepOrAbort(long ms) throws IOException {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            if (ms > 0L) {
                sleeper.sleep(ms);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("RetryInterceptor backoff interrupted", e);
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
