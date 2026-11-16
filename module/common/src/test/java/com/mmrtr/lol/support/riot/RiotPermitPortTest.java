package com.mmrtr.lol.support.riot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RiotPermitPortTest {

    private static final Map<RiotMethod, Integer> COUNTS = Map.of(RiotMethod.MATCH, 1);

    private static RiotPermitPort portGrantingOn(int grantOnCall, AtomicInteger calls) {
        return (platform, priority, counts) -> {
            int n = calls.incrementAndGet();
            if (n < grantOnCall) {
                return Optional.empty();
            }
            return Optional.of(RiotPermits.of(List.of(
                    new RiotPermit("t", platform, RiotMethod.MATCH, priority))));
        };
    }

    @Test
    void acquireWithRetry_retriesUntilGranted() {
        AtomicInteger calls = new AtomicInteger();
        RiotPermitPort port = portGrantingOn(3, calls);

        Optional<RiotPermits> result = port.acquireWithRetry(
                "KR", RiotCallPriority.USER, COUNTS, 5, Duration.ZERO);

        assertThat(result).isPresent();
        assertThat(calls).hasValue(3);
    }

    @Test
    void acquireWithRetry_givesUpAfterAttempts() {
        AtomicInteger calls = new AtomicInteger();
        RiotPermitPort port = portGrantingOn(Integer.MAX_VALUE, calls);

        Optional<RiotPermits> result = port.acquireWithRetry(
                "KR", RiotCallPriority.USER, COUNTS, 4, Duration.ZERO);

        assertThat(result).isEmpty();
        assertThat(calls).hasValue(4);
    }

    @Test
    void acquireWithRetry_interrupted_restoresFlagAndReturnsEmpty() {
        AtomicInteger calls = new AtomicInteger();
        RiotPermitPort port = portGrantingOn(Integer.MAX_VALUE, calls);

        Thread.currentThread().interrupt();
        try {
            Optional<RiotPermits> result = port.acquireWithRetry(
                    "KR", RiotCallPriority.USER, COUNTS, 4, Duration.ofMillis(10));

            assertThat(result).isEmpty();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
