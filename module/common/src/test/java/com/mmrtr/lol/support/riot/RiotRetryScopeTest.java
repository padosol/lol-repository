package com.mmrtr.lol.support.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiotRetryScopeTest {

    @Test
    @DisplayName("스코프 밖에서는 429 재시도가 켜져 있다")
    void rateLimitRetryEnabled_outsideScope_true() {
        // when & then
        assertThat(RiotRetryScope.rateLimitRetryEnabled()).isTrue();
    }

    @Test
    @DisplayName("call 안에서는 꺼지고 끝나면 복원된다")
    void callWithoutRateLimitRetry_insideFalse_restoresAfter() {
        // when
        boolean inside = RiotRetryScope.callWithoutRateLimitRetry(RiotRetryScope::rateLimitRetryEnabled);

        // then
        assertThat(inside).isFalse();
        assertThat(RiotRetryScope.rateLimitRetryEnabled()).isTrue();
    }

    @Test
    @DisplayName("run 안에서는 꺼지고 끝나면 복원된다")
    void runWithoutRateLimitRetry_insideFalse_restoresAfter() {
        // given
        AtomicBoolean inside = new AtomicBoolean(true);

        // when
        RiotRetryScope.runWithoutRateLimitRetry(() -> inside.set(RiotRetryScope.rateLimitRetryEnabled()));

        // then
        assertThat(inside).isFalse();
        assertThat(RiotRetryScope.rateLimitRetryEnabled()).isTrue();
    }

    @Test
    @DisplayName("중첩 스코프는 바깥 상태로 복원되고 예외가 나도 복원된다")
    void nested_and_exception_restores() {
        // when & then
        RiotRetryScope.runWithoutRateLimitRetry(() -> {
            assertThatThrownBy(() -> RiotRetryScope.runWithoutRateLimitRetry(() -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(RiotRetryScope.rateLimitRetryEnabled()).isFalse();
        });
        assertThat(RiotRetryScope.rateLimitRetryEnabled()).isTrue();
    }
}
