package com.mmrtr.lol.infra.rabbitmq.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class RiotFailuresTest {

    private static RiotRateLimitException rateLimit() {
        return new RiotRateLimitException(Duration.ofSeconds(1), null, HttpStatus.TOO_MANY_REQUESTS, "", null);
    }

    @Test
    @DisplayName("429·5xx·IO·시간 초과는 감싸여 있어도 일시 오류다")
    void transientFailures() {
        assertThat(RiotFailures.isTransient(rateLimit())).isTrue();
        assertThat(RiotFailures.isTransient(new CompletionException(
                new RiotServerException(HttpStatus.BAD_GATEWAY, "")))).isTrue();
        assertThat(RiotFailures.isTransient(new CompletionException(
                new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"))))).isTrue();
        assertThat(RiotFailures.isTransient(new TimeoutException())).isTrue();
    }

    @Test
    @DisplayName("4xx(429 제외)와 그 밖의 예외는 일시 오류가 아니다")
    void nonTransientFailures() {
        assertThat(RiotFailures.isTransient(new RiotClientException(HttpStatus.BAD_REQUEST, "", null))).isFalse();
        assertThat(RiotFailures.isTransient(new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null)))
                .isFalse();
        assertThat(RiotFailures.isTransient(new IllegalStateException("boom"))).isFalse();
        assertThat(RiotFailures.isTransient(null)).isFalse();
    }

    @Test
    @DisplayName("4xx 는 429 를 빼고 감싸여 있어도 클라이언트 오류다")
    void clientErrors() {
        assertThat(RiotFailures.isClientError(new CompletionException(
                new RiotClientException(HttpStatus.BAD_REQUEST, "", null)))).isTrue();
        assertThat(RiotFailures.isClientError(new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "", null)))
                .isTrue();
        assertThat(RiotFailures.isClientError(rateLimit())).isFalse();
        assertThat(RiotFailures.isClientError(new RiotClientException(HttpStatus.TOO_MANY_REQUESTS, "", null)))
                .isFalse();
        assertThat(RiotFailures.isClientError(new RiotServerException(HttpStatus.BAD_GATEWAY, ""))).isFalse();
        assertThat(RiotFailures.isClientError(null)).isFalse();
    }
}
