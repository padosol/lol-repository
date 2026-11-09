package com.mmrtr.lol.infra.riot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.infra.riot.ratelimit.RiotRateLimiter;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

class RiotCallRecorderTest {

    private static final String HOST = "https://asia.api.riotgames.com";
    private static final String METHOD = "match-v5.match";

    private final FakeLimiter limiter = new FakeLimiter();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RiotCallRecorder recorder = new RiotCallRecorder(limiter, new RiotAPIProperties(), meterRegistry);

    @Test
    @DisplayName("200·500·IO 예외 어느 경우든 그 permit 을 한 번 complete 한다")
    void completesPermitOnSuccessAndOnFailure() {
        // given
        RiotPermit ok = permit("ok");
        RiotPermit serverError = permit("5xx");
        RiotPermit io = permit("io");

        // when
        String body = recorder.exchange(ok, HOST, () -> ResponseEntity.ok("body"));
        assertThatThrownBy(() -> recorder.exchange(serverError, HOST, () -> {
            throw new RiotServerException(HttpStatus.INTERNAL_SERVER_ERROR, "x");
        })).isInstanceOf(RiotServerException.class);
        assertThatThrownBy(() -> recorder.exchange(io, HOST, () -> {
            throw new ResourceAccessException("I/O error", new IOException("reset"));
        })).isInstanceOf(ResourceAccessException.class);

        // then
        assertThat(body).isEqualTo("body");
        assertThat(limiter.completed).containsExactly(ok, serverError, io);
        assertThat(responses("200")).isEqualTo(1);
        assertThat(responses("500")).isEqualTo(1);
    }

    @Test
    @DisplayName("complete 가 실패해도 응답 본문을 돌려준다")
    void completeFailure_stillReturnsBody() {
        // given
        limiter.completeFailure = new IllegalStateException("redis down");

        // when
        String body = recorder.exchange(permit("ok"), HOST, () -> ResponseEntity.ok("body"));

        // then
        assertThat(body).isEqualTo("body");
    }

    @Test
    @DisplayName("응답 헤더의 app·method 한도를 배운다")
    void learnsLimitsFromHeaders() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-App-Rate-Limit", "500:10");
        headers.add("X-Method-Rate-Limit", "2000:10");

        // when
        recorder.exchange(permit("ok"), HOST, () -> ResponseEntity.ok().headers(headers).body("body"));

        // then
        assertThat(limiter.learned).containsExactly("asia|" + METHOD + "|500:10|2000:10");
    }

    @Test
    @DisplayName("4xx 응답의 헤더로도 한도를 배운다")
    void learnsLimitsFromErrorResponseHeaders() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-App-Rate-Limit", "500:10");

        // when
        assertThatThrownBy(() -> recorder.exchange(permit("404"), HOST, () -> {
            throw new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "x", LogLevel.WARN, headers);
        })).isInstanceOf(RiotClientNotFoundException.class);

        // then
        assertThat(limiter.learned).containsExactly("asia|" + METHOD + "|500:10|null");
        assertThat(responses("404")).isEqualTo(1);
    }

    @Test
    @DisplayName("count 가 한도의 95% 이상이면 그 버킷을 1초 정지하고 scope 별로 센다")
    void pausesNearLimit() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-App-Rate-Limit", "500:10");
        headers.add("X-App-Rate-Limit-Count", "476:10");
        headers.add("X-Method-Rate-Limit", "2000:10");
        headers.add("X-Method-Rate-Limit-Count", "1:10");

        // when
        recorder.exchange(permit("ok"), HOST, () -> ResponseEntity.ok().headers(headers).body("body"));

        // then
        assertThat(limiter.pauses).containsExactly("asia|null|PT1S");
        assertThat(meterRegistry.get("riot.ratelimit.near_limit").tags("routing", "asia", "scope", "app")
                .counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find("riot.ratelimit.near_limit").tags("scope", "method").counter()).isNull();
    }

    @Test
    @DisplayName("429 는 type 이 application 이면 routing 전체, 그 밖(없음 포함)이면 그 method 를 Retry-After 만큼 정지한다")
    void pausesOn429ByType() {
        // when
        call429(Duration.ofSeconds(7), "application");
        call429(Duration.ofSeconds(7), "method");
        call429(Duration.ofSeconds(7), null);
        call429(null, "method");

        // then
        assertThat(limiter.pauses).containsExactly(
                "asia|null|PT7S",
                "asia|" + METHOD + "|PT7S",
                "asia|" + METHOD + "|PT7S",
                "asia|" + METHOD + "|PT1S");
        assertThat(rateLimited("application")).isEqualTo(1);
        assertThat(rateLimited("method")).isEqualTo(2);
        assertThat(rateLimited("none")).isEqualTo(1);
        assertThat(responses("429")).isEqualTo(4);
    }

    @Test
    @DisplayName("429 정지가 실패해도 429 예외는 그대로 올라간다")
    void pauseFailure_still429ToCaller() {
        // given
        limiter.pauseFailure = new IllegalStateException("redis down");

        // when // then
        assertThatThrownBy(() -> recorder.exchange(permit("429"), HOST, () -> {
            throw rateLimited(Duration.ofSeconds(7), "application");
        })).isInstanceOf(RiotRateLimitException.class);
        assertThat(limiter.completed).hasSize(1);
    }

    @Test
    @DisplayName("429·5xx 에서 다시 보내지 않는다")
    void doesNotResend() {
        // given
        AtomicInteger calls = new AtomicInteger();

        // when
        assertThatThrownBy(() -> recorder.exchange(permit("429"), HOST, counting(calls, () -> {
            throw rateLimited(Duration.ofSeconds(1), "application");
        }))).isInstanceOf(RiotRateLimitException.class);
        assertThatThrownBy(() -> recorder.exchange(permit("503"), HOST, counting(calls, () -> {
            throw new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "x");
        }))).isInstanceOf(RiotServerException.class);

        // then
        assertThat(calls).hasValue(2);
    }

    private void call429(Duration retryAfter, String type) {
        assertThatThrownBy(() -> recorder.exchange(permit("429"), HOST, () -> {
            throw rateLimited(retryAfter, type);
        })).isInstanceOf(RiotRateLimitException.class);
    }

    private static RiotRateLimitException rateLimited(Duration retryAfter, String type) {
        return new RiotRateLimitException(retryAfter, type, HttpStatus.TOO_MANY_REQUESTS, "", LogLevel.WARN);
    }

    private static <T> Supplier<ResponseEntity<T>> counting(AtomicInteger calls, Supplier<ResponseEntity<T>> call) {
        return () -> {
            calls.incrementAndGet();
            return call.get();
        };
    }

    private static RiotPermit permit(String token) {
        return new RiotPermit(token, "KR", RiotMethod.MATCH, RiotCallPriority.USER);
    }

    private double responses(String status) {
        var counter = meterRegistry.find("riot.api.responses")
                .tags("status", status, "host", "asia.api.riotgames.com").counter();
        return counter == null ? 0 : counter.count();
    }

    private double rateLimited(String type) {
        var counter = meterRegistry.find("riot.api.rate_limited").tags("routing", "asia", "type", type).counter();
        return counter == null ? 0 : counter.count();
    }

    static class FakeLimiter implements RiotRateLimiter {
        final List<RiotPermit> completed = new ArrayList<>();
        final List<String> learned = new ArrayList<>();
        final List<String> pauses = new ArrayList<>();
        RuntimeException completeFailure;
        RuntimeException pauseFailure;

        @Override
        public synchronized void complete(RiotPermit permit) {
            completed.add(permit);
            if (completeFailure != null) {
                throw completeFailure;
            }
        }

        @Override
        public synchronized void learnLimits(String routing, String method, String app, String methodHeader) {
            learned.add(routing + "|" + method + "|" + app + "|" + methodHeader);
        }

        @Override
        public synchronized void pause(String routing, String methodOrNull, Duration duration) {
            if (pauseFailure != null) {
                throw pauseFailure;
            }
            pauses.add(routing + "|" + methodOrNull + "|" + duration);
        }
    }
}
