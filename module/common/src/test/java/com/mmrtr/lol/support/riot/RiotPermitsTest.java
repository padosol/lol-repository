package com.mmrtr.lol.support.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RiotPermitsTest {

    private static RiotPermit permit(String token, RiotMethod method) {
        return new RiotPermit(token, "KR", method, RiotCallPriority.USER);
    }

    @Test
    void takeReturnsEachTokenOnce_thenThrows() {
        RiotPermits permits = RiotPermits.of(List.of(
                permit("a", RiotMethod.MATCH), permit("b", RiotMethod.MATCH)));

        Set<String> tokens = Set.of(
                permits.take(RiotMethod.MATCH).token(), permits.take(RiotMethod.MATCH).token());

        assertThat(tokens).containsExactlyInAnyOrder("a", "b");
        assertThatThrownBy(() -> permits.take(RiotMethod.MATCH))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> permits.take(RiotMethod.MATCH_TIMELINE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentTakeYieldsDistinctTokens() throws Exception {
        List<RiotPermit> all = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            all.add(permit("t" + i, RiotMethod.MATCH));
        }
        RiotPermits permits = RiotPermits.of(all);
        Set<String> seen = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < 10; i++) {
                    seen.add(permits.take(RiotMethod.MATCH).token());
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(seen).hasSize(80);
        assertThat(permits.remaining(RiotMethod.MATCH)).isZero();
    }

    @Test
    void remainingCountsPerMethod() {
        RiotPermits permits = RiotPermits.of(List.of(
                permit("a", RiotMethod.MATCH),
                permit("b", RiotMethod.MATCH),
                permit("c", RiotMethod.MATCH_TIMELINE)));

        assertThat(permits.remaining(RiotMethod.MATCH)).isEqualTo(2);
        assertThat(permits.remaining(RiotMethod.MATCH_TIMELINE)).isEqualTo(1);
        assertThat(permits.remaining(RiotMethod.ACTIVE_GAME)).isZero();

        permits.take(RiotMethod.MATCH);
        assertThat(permits.remaining(RiotMethod.MATCH)).isEqualTo(1);
    }
}
