package com.mmrtr.lol.support.riot;

import static com.mmrtr.lol.support.riot.RiotCallPriority.BACKGROUND;
import static com.mmrtr.lol.support.riot.RiotCallPriority.DEFERRED;
import static com.mmrtr.lol.support.riot.RiotCallPriority.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RiotCallScopeTest {

    @Test
    void current_outsideScope_isUser() {
        assertThat(RiotCallScope.current()).isEqualTo(USER);
    }

    @Test
    void run_setsPriorityInside() {
        RiotCallScope.run(BACKGROUND, () -> assertThat(RiotCallScope.current()).isEqualTo(BACKGROUND));

        assertThat(RiotCallScope.current()).isEqualTo(USER);
    }

    @Test
    void call_returnsBodyResult() {
        RiotCallPriority result = RiotCallScope.call(DEFERRED, RiotCallScope::current);

        assertThat(result).isEqualTo(DEFERRED);
    }

    @Test
    void nestedScope_restoresOuter() {
        RiotCallScope.run(BACKGROUND, () -> {
            RiotCallScope.run(DEFERRED, () -> assertThat(RiotCallScope.current()).isEqualTo(DEFERRED));

            assertThat(RiotCallScope.current()).isEqualTo(BACKGROUND);
        });
    }

    @Test
    void exception_restoresPrevious() {
        assertThatThrownBy(() -> RiotCallScope.run(BACKGROUND, () -> {
            throw new IllegalStateException();
        })).isInstanceOf(IllegalStateException.class);

        assertThat(RiotCallScope.current()).isEqualTo(USER);
    }

    @Test
    void otherThread_doesNotInherit() {
        AtomicReference<RiotCallPriority> seen = new AtomicReference<>();

        RiotCallScope.run(BACKGROUND, () -> {
            Thread thread = new Thread(() -> seen.set(RiotCallScope.current()));
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(seen.get()).isEqualTo(USER);
    }
}
