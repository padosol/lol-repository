package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class RenewalFailureReasonTest {

    @Test
    @DisplayName("Riot 404 는 riot_404 다")
    void of_Riot404_riot_404() {
        // given
        Throwable t = new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "x", LogLevel.WARN);

        // when
        String reason = RenewalFailureReason.of(t);

        // then
        assertThat(reason).isEqualTo("riot_404");
    }

    @Test
    @DisplayName("status 429 인 RiotClientException 은 riot_429 다")
    void of_Riot429_riot_429() {
        // given
        Throwable t = new RiotClientException(HttpStatus.TOO_MANY_REQUESTS, "x", LogLevel.WARN);

        // when
        String reason = RenewalFailureReason.of(t);

        // then
        assertThat(reason).isEqualTo("riot_429");
    }

    @Test
    @DisplayName("그 밖의 RiotClientException 은 riot_4xx 다")
    void of_그밖의Riot4xx_riot_4xx() {
        // given
        Throwable t = new RiotClientException(HttpStatus.FORBIDDEN, "x", LogLevel.WARN);

        // when
        String reason = RenewalFailureReason.of(t);

        // then
        assertThat(reason).isEqualTo("riot_4xx");
    }

    @Test
    @DisplayName("RiotServerException 은 riot_5xx 다")
    void of_Riot5xx_riot_5xx() {
        // given
        Throwable t = new RiotServerException(HttpStatus.BAD_GATEWAY, "x");

        // when
        String reason = RenewalFailureReason.of(t);

        // then
        assertThat(reason).isEqualTo("riot_5xx");
    }

    @Test
    @DisplayName("TimeoutException 은 timeout 이다")
    void of_TimeoutException_timeout() {
        // given
        Throwable t = new TimeoutException("t");

        // when
        String reason = RenewalFailureReason.of(t);

        // then
        assertThat(reason).isEqualTo("timeout");
    }

    @Test
    @DisplayName("HTTP 읽기 시간 초과(ResourceAccessException 이 감싼 HttpTimeoutException)는 timeout 이다")
    void of_HTTP읽기시간초과_timeout() {
        // given: RestClient 가 JDK HttpClient 의 시간 초과를 감싸 올리는 모양 그대로, 비동기 래퍼까지 씌운다
        Throwable t = new CompletionException(
                new ResourceAccessException("I/O error on GET request", new HttpTimeoutException("request timed out")));

        // when
        String reason = RenewalFailureReason.of(t);

        // then
        assertThat(reason).isEqualTo("timeout");
    }

    @Test
    @DisplayName("소켓 시간 초과(SocketTimeoutException)가 cause 에 있으면 timeout 이다")
    void of_소켓시간초과원인_timeout() {
        // given
        Throwable t = new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

        // when
        String reason = RenewalFailureReason.of(t);

        // then
        assertThat(reason).isEqualTo("timeout");
    }

    @Test
    @DisplayName("TimeoutException 이 cause 사슬 깊이 있어도 timeout 이다")
    void of_깊은원인의TimeoutException_timeout() {
        // given
        Throwable t = new IllegalStateException("outer", new RuntimeException("middle", new TimeoutException("inner")));

        // when
        String reason = RenewalFailureReason.of(t);

        // then
        assertThat(reason).isEqualTo("timeout");
    }

    @Test
    @DisplayName("cause 가 서로를 가리켜도 멈추고 other 다")
    void of_순환원인_other() {
        // given
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        // when
        String reason = RenewalFailureReason.of(a);

        // then
        assertThat(reason).isEqualTo("other");
    }

    @Test
    @DisplayName("시간 초과가 아닌 I/O 오류는 other 다")
    void of_시간초과아닌IO오류_other() {
        // given
        Throwable t = new ResourceAccessException("I/O error", new IOException("connection reset"));

        // when
        String reason = RenewalFailureReason.of(t);

        // then
        assertThat(reason).isEqualTo("other");
    }

    @Test
    @DisplayName("모르는 예외와 null 은 other 다")
    void of_모르는예외와null_other() {
        // given
        Throwable unknown = new IllegalStateException("x");

        // when
        String unknownReason = RenewalFailureReason.of(unknown);
        String nullReason = RenewalFailureReason.of(null);

        // then
        assertThat(unknownReason).isEqualTo("other");
        assertThat(nullReason).isEqualTo("other");
    }

    @Test
    @DisplayName("CompletionException·ExecutionException 은 cause 로 풀어서 본다")
    void of_비동기래퍼_cause로판정() {
        // given
        Throwable cause = new RiotServerException(HttpStatus.BAD_GATEWAY, "x");

        // when
        String completion = RenewalFailureReason.of(new CompletionException(cause));
        String nested = RenewalFailureReason.of(new ExecutionException(new CompletionException(cause)));

        // then
        assertThat(completion).isEqualTo("riot_5xx");
        assertThat(nested).isEqualTo("riot_5xx");
    }
}
