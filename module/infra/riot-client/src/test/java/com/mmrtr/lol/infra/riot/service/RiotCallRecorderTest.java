package com.mmrtr.lol.infra.riot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.infra.riot.ratelimit.RiotLimitHeaders;
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
import java.util.concurrent.atomic.AtomicReference;
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
    @DisplayName("429 가 아닌 응답(200·404·500·IO 예외) 뒤에는 정지하지 않는다")
    void nonRateLimitedResponses_doNotPause() {
        // given
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-App-Rate-Limit", "500:10");
        headers.add("X-App-Rate-Limit-Count", "499:10");

        // when
        String body = recorder.exchange(permit("ok"), HOST, () -> ResponseEntity.ok().headers(headers).body("body"));
        assertThatThrownBy(() -> recorder.exchange(permit("404"), HOST, () -> {
            throw new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "x", LogLevel.WARN, headers);
        })).isInstanceOf(RiotClientNotFoundException.class);
        assertThatThrownBy(() -> recorder.exchange(permit("5xx"), HOST, () -> {
            throw new RiotServerException(HttpStatus.INTERNAL_SERVER_ERROR, "x");
        })).isInstanceOf(RiotServerException.class);
        assertThatThrownBy(() -> recorder.exchange(permit("io"), HOST, () -> {
            throw new ResourceAccessException("I/O error", new IOException("reset"));
        })).isInstanceOf(ResourceAccessException.class);

        // then
        assertThat(body).isEqualTo("body");
        assertThat(limiter.pauses).isEmpty();
        assertThat(responses("200")).isEqualTo(1);
        assertThat(responses("404")).isEqualTo(1);
        assertThat(responses("500")).isEqualTo(1);
    }

    @Test
    @DisplayName("한도 헤더가 있는 응답(200·404·429·500)마다 routing·method 와 헤더 네 개를 한도 관리자에 넘긴다")
    void forwardsRateLimitHeaders_onEveryResponseWithHeaders() {
        // given
        HttpHeaders headers = limitHeaders();

        // when
        recorder.exchange(permit("ok"), HOST, () -> ResponseEntity.ok().headers(headers).body("body"));
        assertThatThrownBy(() -> recorder.exchange(permit("404"), HOST, () -> {
            throw new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "x", LogLevel.WARN, headers);
        })).isInstanceOf(RiotClientNotFoundException.class);
        assertThatThrownBy(() -> recorder.exchange(permit("429"), HOST, () -> {
            throw new RiotRateLimitException(Duration.ofSeconds(1), "method", HttpStatus.TOO_MANY_REQUESTS, "",
                    LogLevel.WARN, headers);
        })).isInstanceOf(RiotRateLimitException.class);
        assertThatThrownBy(() -> recorder.exchange(permit("5xx"), HOST, () -> {
            throw new RiotServerException(HttpStatus.INTERNAL_SERVER_ERROR, "x", headers);
        })).isInstanceOf(RiotServerException.class);

        // then
        String expected = "asia|" + METHOD + "|500:10,30000:600|3:10,40:600|2000:10|3:10";
        assertThat(limiter.observations).containsExactly(expected, expected, expected, expected);
    }

    @Test
    @DisplayName("한도 헤더가 하나도 없는 응답과 IO 예외는 넘기지 않고, 일부만 있으면 없는 값은 null 로 넘긴다")
    void missingRateLimitHeaders() {
        // given
        HttpHeaders appOnly = new HttpHeaders();
        appOnly.add("X-App-Rate-Limit", "20:1");

        // when
        recorder.exchange(permit("plain"), HOST, () -> ResponseEntity.ok().body("body"));
        assertThatThrownBy(() -> recorder.exchange(permit("io"), HOST, () -> {
            throw new ResourceAccessException("I/O error", new IOException("reset"));
        })).isInstanceOf(ResourceAccessException.class);
        recorder.exchange(permit("app-only"), HOST, () -> ResponseEntity.ok().headers(appOnly).body("body"));

        // then
        assertThat(limiter.observations).containsExactly("asia|" + METHOD + "|20:1|null|null|null");
    }

    @Test
    @DisplayName("한도 헤더 기록이 실패해도 응답 본문은 그대로 돌려준다")
    void observeFailure_stillReturnsBody() {
        // given
        limiter.observeFailure = new IllegalStateException("redis down");

        // when
        String body = recorder.exchange(permit("ok"), HOST,
                () -> ResponseEntity.ok().headers(limitHeaders()).body("body"));

        // then
        assertThat(body).isEqualTo("body");
        assertThat(responses("200")).isEqualTo(1);
    }

    @Test
    @DisplayName("호출마다 걸린 시간을 호스트·method·결과(ok·error)별로 남긴다")
    void recordsCallDuration_byResult() {
        // when
        recorder.exchange(permit("ok"), HOST, () -> ResponseEntity.ok().body("body"));
        assertThatThrownBy(() -> recorder.exchange(permit("404"), HOST, () -> {
            throw new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "x", LogLevel.WARN);
        })).isInstanceOf(RiotClientNotFoundException.class);
        assertThatThrownBy(() -> recorder.exchange(permit("io"), HOST, () -> {
            throw new ResourceAccessException("I/O error", new IOException("reset"));
        })).isInstanceOf(ResourceAccessException.class);

        // then
        assertThat(callCount("ok")).isEqualTo(1);
        assertThat(callCount("error")).isEqualTo(2);
    }

    @Test
    @DisplayName("응답 헤더를 받지 못한 호출(인터셉터를 거치지 않은 호출 포함)은 대기·받기 시간과 크기를 남기지 않는다")
    void noHeaders_noWaitDownloadOrSize() {
        // when
        recorder.exchange(permit("ok"), HOST, () -> ResponseEntity.ok().body("body"));

        // then
        assertThat(meterRegistry.find("riot.api.call.wait").timer()).isNull();
        assertThat(meterRegistry.find("riot.api.call.download").timer()).isNull();
        assertThat(meterRegistry.find("riot.api.response.size").summary()).isNull();
    }

    @Test
    @DisplayName("호출 중에는 그 호스트의 진행 중 호출 수가 1 이고, 끝나면 0 이며 호출 기록도 스레드에서 지운다")
    void inflightDuringCall_andTimingClearedAfter() {
        // given
        AtomicReference<Double> during = new AtomicReference<>();
        AtomicReference<RiotCallTiming> timingDuring = new AtomicReference<>();

        // when
        recorder.exchange(permit("ok"), HOST, () -> {
            during.set(inflight());
            timingDuring.set(RiotCallTiming.current());
            return ResponseEntity.ok().body("body");
        });

        // then
        assertThat(during.get()).isEqualTo(1.0);
        assertThat(inflight()).isZero();
        assertThat(timingDuring.get()).isNotNull();
        assertThat(RiotCallTiming.current()).isNull();
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

    private static HttpHeaders limitHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-App-Rate-Limit", "500:10,30000:600");
        headers.add("X-App-Rate-Limit-Count", "3:10,40:600");
        headers.add("X-Method-Rate-Limit", "2000:10");
        headers.add("X-Method-Rate-Limit-Count", "3:10");
        return headers;
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

    private long callCount(String result) {
        var timer = meterRegistry.find("riot.api.call.duration")
                .tags("host", "asia.api.riotgames.com", "method", METHOD, "result", result).timer();
        return timer == null ? 0 : timer.count();
    }

    private double inflight() {
        var gauge = meterRegistry.find("riot.api.inflight").tags("host", "asia.api.riotgames.com").gauge();
        return gauge == null ? -1 : gauge.value();
    }

    private double rateLimited(String type) {
        var counter = meterRegistry.find("riot.api.rate_limited").tags("routing", "asia", "type", type).counter();
        return counter == null ? 0 : counter.count();
    }

    static class FakeLimiter implements RiotRateLimiter {
        final List<String> pauses = new ArrayList<>();
        final List<String> observations = new ArrayList<>();
        RuntimeException pauseFailure;
        RuntimeException observeFailure;

        @Override
        public synchronized void observe(String routing, String method, RiotLimitHeaders headers) {
            if (observeFailure != null) {
                throw observeFailure;
            }
            observations.add(String.join("|", routing, method, headers.appLimit(), headers.appCount(),
                    headers.methodLimit(), headers.methodCount()));
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
