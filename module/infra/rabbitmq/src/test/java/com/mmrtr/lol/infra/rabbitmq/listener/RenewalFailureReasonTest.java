package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class RenewalFailureReasonTest {

    @Test
    @DisplayName("예산 초과는 budget_exceeded 다 (429 인 상위 클래스보다 먼저 본다)")
    void of_budgetExceeded() {
        Throwable t = new RiotBudgetExceededException("asia", RiotCallPriority.USER);

        assertThat(RenewalFailureReason.of(t)).isEqualTo("budget_exceeded");
    }

    @Test
    @DisplayName("Riot 404 는 riot_404 다")
    void of_notFound() {
        Throwable t = new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "x", LogLevel.WARN);

        assertThat(RenewalFailureReason.of(t)).isEqualTo("riot_404");
    }

    @Test
    @DisplayName("status 429 인 RiotClientException 은 riot_429 다")
    void of_tooManyRequests() {
        Throwable t = new RiotClientException(HttpStatus.TOO_MANY_REQUESTS, "x", LogLevel.WARN);

        assertThat(RenewalFailureReason.of(t)).isEqualTo("riot_429");
    }

    @Test
    @DisplayName("그 밖의 RiotClientException 은 riot_4xx 다")
    void of_otherClient() {
        Throwable t = new RiotClientException(HttpStatus.FORBIDDEN, "x", LogLevel.WARN);

        assertThat(RenewalFailureReason.of(t)).isEqualTo("riot_4xx");
    }

    @Test
    @DisplayName("RiotServerException 은 riot_5xx 다")
    void of_server() {
        Throwable t = new RiotServerException(HttpStatus.BAD_GATEWAY, "x");

        assertThat(RenewalFailureReason.of(t)).isEqualTo("riot_5xx");
    }

    @Test
    @DisplayName("TimeoutException 은 timeout 이다")
    void of_timeout() {
        assertThat(RenewalFailureReason.of(new TimeoutException("t"))).isEqualTo("timeout");
    }

    @Test
    @DisplayName("모르는 예외와 null 은 other 다")
    void of_other() {
        assertThat(RenewalFailureReason.of(new IllegalStateException("x"))).isEqualTo("other");
        assertThat(RenewalFailureReason.of(null)).isEqualTo("other");
    }

    @Test
    @DisplayName("CompletionException·ExecutionException 은 cause 로 풀어서 본다")
    void of_unwrapsWrappers() {
        Throwable cause = new RiotServerException(HttpStatus.BAD_GATEWAY, "x");

        assertThat(RenewalFailureReason.of(new CompletionException(cause))).isEqualTo("riot_5xx");
        assertThat(RenewalFailureReason.of(new ExecutionException(new CompletionException(cause))))
                .isEqualTo("riot_5xx");
    }
}
