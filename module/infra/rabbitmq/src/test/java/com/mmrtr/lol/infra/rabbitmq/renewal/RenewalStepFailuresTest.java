package com.mmrtr.lol.infra.rabbitmq.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

class RenewalStepFailuresTest {

    private final RenewalFinisher finisher = mock(RenewalFinisher.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RenewalStepFailures failures =
            new RenewalStepFailures("summoner", mock(RenewalRecordPort.class), finisher, meters);

    private double requeues() {
        var counter = meters.find("lol.renewal.step").tags("step", "summoner", "result", "requeue").counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("5xx 는 MAX_ATTEMPT 전까지 시도 수를 올려 다시 넣고, 그 뒤엔 riot_5xx 로 실패시킨다")
    void serverErrorRequeuesUntilMaxAttemptThenFails() {
        // given
        Exception e = new CompletionException(new RiotServerException(HttpStatus.BAD_GATEWAY, "x"));
        AtomicInteger requeued = new AtomicInteger();

        // when: 시도 0, 1 은 다시 넣는다
        for (int attempt = 0; attempt < RenewalStepFailures.MAX_ATTEMPT; attempt++) {
            failures.fetchFailed("p1", "r1", attempt, e, requeued::incrementAndGet);
        }

        // then
        assertThat(requeued).hasValue(2);
        assertThat(requeues()).isEqualTo(2.0);
        verify(finisher, never()).fail(anyString(), anyString(), anyString());

        // when: MAX_ATTEMPT 번째 시도는 실패시킨다
        failures.fetchFailed("p1", "r1", RenewalStepFailures.MAX_ATTEMPT, e, requeued::incrementAndGet);

        // then
        assertThat(requeued).hasValue(2);
        verify(finisher).fail("p1", "r1", "riot_5xx");
    }

    @Test
    @DisplayName("429·IO·시간 초과도 다시 넣는다")
    void rateLimitIoAndTimeoutAreRetried() {
        AtomicInteger requeued = new AtomicInteger();
        failures.fetchFailed("p1", "r1", 0, rateLimit(), requeued::incrementAndGet);
        failures.fetchFailed("p1", "r1", 0, new IllegalStateException(new IOException("reset")),
                requeued::incrementAndGet);
        failures.fetchFailed("p1", "r1", 0, new CompletionException(new TimeoutException()),
                requeued::incrementAndGet);

        assertThat(requeued).hasValue(3);
        verify(finisher, never()).fail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("404 는 not_found, 그 밖의 4xx 는 다시 넣지 않고 바로 실패시킨다")
    void notFoundAndClientErrorsAreNotRetried() {
        AtomicInteger requeued = new AtomicInteger();

        failures.fetchFailed("p1", "r1", 0,
                new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "x", LogLevel.WARN), requeued::incrementAndGet);
        failures.fetchFailed("p2", "r2", 0,
                new RiotClientException(HttpStatus.FORBIDDEN, "x", LogLevel.WARN), requeued::incrementAndGet);

        assertThat(requeued).hasValue(0);
        verify(finisher).fail("p1", "r1", "not_found");
        verify(finisher).fail("p2", "r2", "riot_4xx");
    }

    private static RiotRateLimitException rateLimit() {
        return new RiotRateLimitException(Duration.ofSeconds(1), null, HttpStatus.TOO_MANY_REQUESTS, "", null);
    }
}
