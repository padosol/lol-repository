package com.mmrtr.lol.tools.riotmock.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlidingPeakTrackerTest {

    private final AtomicLong now = new AtomicLong(0);
    private final SlidingPeakTracker tracker = new SlidingPeakTracker(now::get);

    private void recordAt(long millis, int times) {
        now.set(millis);
        for (int i = 0; i < times; i++) {
            tracker.record("asia");
        }
    }

    @Test
    @DisplayName("고정 창 경계를 걸친 몰림도 잡는다")
    void peak_고정창_경계를_걸친_몰림도_잡는다() {
        // given
        tracker.trackWindows(List.of(10));
        for (long t = 9000; t < 9500; t += 100) {
            recordAt(t, 1);
        }
        for (long t = 10000; t < 10500; t += 100) {
            recordAt(t, 1);
        }

        // when
        int peak = tracker.peak("asia", 10);

        // then
        assertThat(peak).isEqualTo(10);
    }

    @Test
    @DisplayName("W 초가 지난 호출은 빠진다")
    void peak_W초가_지난_호출은_빠진다() {
        // given
        tracker.trackWindows(List.of(10));
        recordAt(0, 5);
        recordAt(10100, 5);

        // when
        int peak = tracker.peak("asia", 10);

        // then
        assertThat(peak).isEqualTo(5);
    }

    @Test
    @DisplayName("reset 뒤에는 0")
    void reset_뒤에는_0() {
        // given
        tracker.trackWindows(List.of(10));
        recordAt(0, 5);

        // when
        tracker.reset();

        // then
        assertThat(tracker.peak("asia", 10)).isZero();
    }
}
