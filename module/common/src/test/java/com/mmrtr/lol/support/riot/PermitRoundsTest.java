package com.mmrtr.lol.support.riot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PermitRoundsTest {

    private static final Map<RiotMethod, Integer> ONE_MATCH = Map.of(RiotMethod.MATCH, 1);

    /** 요청한 counts 를 기록하고, 판정 함수로 허가 여부를 정하는 가짜 포트. */
    private static class FakePort implements RiotPermitPort {
        final List<Map<RiotMethod, Integer>> requested = new ArrayList<>();
        private final Predicate<Map<RiotMethod, Integer>> allow;

        FakePort(Predicate<Map<RiotMethod, Integer>> allow) {
            this.allow = allow;
        }

        @Override
        public Optional<RiotPermits> tryAcquire(String platform, RiotCallPriority priority,
                                                Map<RiotMethod, Integer> counts) {
            requested.add(counts);
            if (!allow.test(counts)) {
                return Optional.empty();
            }
            return Optional.of(RiotPermits.of(List.of()));
        }

        List<Integer> requestedMatchCounts() {
            return requested.stream().map(m -> m.getOrDefault(RiotMethod.MATCH, 0)).toList();
        }
    }

    private static List<Integer> items(int n) {
        return IntStream.range(0, n).boxed().toList();
    }

    @Test
    void sizes_halveUntilOne() {
        assertThat(PermitRounds.sizes(20)).containsExactly(20, 10, 5, 3, 2, 1);
        assertThat(PermitRounds.sizes(7)).containsExactly(7, 4, 2, 1);
        assertThat(PermitRounds.sizes(2)).containsExactly(2, 1);
        assertThat(PermitRounds.sizes(1)).containsExactly(1);
        assertThat(PermitRounds.sizes(0)).containsExactly(1);
    }

    @Test
    void allDenied_asksDownToOne_returnsAllItems() {
        FakePort port = new FakePort(c -> false);
        List<List<Integer>> granted = new ArrayList<>();

        List<Integer> denied = PermitRounds.acquire(port, "KR", RiotCallPriority.DEFERRED,
                items(20), 20, i -> ONE_MATCH, (chunk, permits) -> granted.add(chunk));

        assertThat(port.requestedMatchCounts()).containsExactly(20, 10, 5, 3, 2, 1);
        assertThat(granted).isEmpty();
        assertThat(denied).isEqualTo(items(20));
    }

    @Test
    void grantedChunk_thenContinuesWithRest() {
        FakePort port = new FakePort(c -> c.get(RiotMethod.MATCH) <= 10);
        List<List<Integer>> granted = new ArrayList<>();

        List<Integer> denied = PermitRounds.acquire(port, "KR", RiotCallPriority.DEFERRED,
                items(20), 20, i -> ONE_MATCH, (chunk, permits) -> granted.add(chunk));

        assertThat(port.requestedMatchCounts()).containsExactly(20, 10, 10);
        assertThat(granted).containsExactly(items(20).subList(0, 10), items(20).subList(10, 20));
        assertThat(denied).isEmpty();
    }

    @Test
    void costIsSummedPerMethod() {
        FakePort port = new FakePort(c -> false);

        PermitRounds.acquire(port, "KR", RiotCallPriority.DEFERRED, items(5), 5,
                i -> i < 3
                        ? Map.of(RiotMethod.MATCH, 1, RiotMethod.MATCH_TIMELINE, 1)
                        : ONE_MATCH,
                (chunk, permits) -> { });

        assertThat(port.requested.get(0))
                .isEqualTo(Map.of(RiotMethod.MATCH, 5, RiotMethod.MATCH_TIMELINE, 3));
    }

    @Test
    void maxSizeCapsFirstAttempt() {
        FakePort port = new FakePort(c -> false);

        PermitRounds.acquire(port, "KR", RiotCallPriority.DEFERRED, items(30), 20,
                i -> ONE_MATCH, (chunk, permits) -> { });

        assertThat(port.requestedMatchCounts().get(0)).isEqualTo(20);
    }

    @Test
    void singleItem_askedOnce() {
        FakePort port = new FakePort(c -> false);

        List<Integer> denied = PermitRounds.acquire(port, "KR", RiotCallPriority.DEFERRED,
                items(1), 20, i -> ONE_MATCH, (chunk, permits) -> { });

        assertThat(port.requested).hasSize(1);
        assertThat(denied).containsExactly(0);
    }
}
