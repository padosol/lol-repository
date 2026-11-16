package com.mmrtr.lol.tools.riotmock.ratelimit;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * 100ms 칸으로 호출을 세어, 추적하는 창 W 마다 직전 W*10 칸의 합(슬라이딩)의 최댓값을 유지한다.
 * 고정 창 경계를 걸친 몰림도 잡는다. 메모리는 routing 당 가장 큰 W*10 칸으로 제한된다.
 */
public class SlidingPeakTracker {

    private static final long BUCKET_MILLIS = 100;
    private static final int BUCKETS_PER_SECOND = 10;

    private final LongSupplier clockMillis;
    private final Map<String, State> states = new HashMap<>();
    private int[] windows = new int[0];

    public SlidingPeakTracker(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public synchronized void trackWindows(Collection<Integer> windowSeconds) {
        this.windows = windowSeconds.stream().distinct().sorted().mapToInt(Integer::intValue).toArray();
        states.clear();
    }

    public synchronized void record(String routing) {
        if (windows.length == 0) {
            return;
        }
        State state = states.computeIfAbsent(routing, k -> new State(windows));
        state.record(clockMillis.getAsLong() / BUCKET_MILLIS);
    }

    public synchronized int peak(String routing, int windowSeconds) {
        State state = states.get(routing);
        if (state == null) {
            return 0;
        }
        for (int i = 0; i < windows.length; i++) {
            if (windows[i] == windowSeconds) {
                return state.peaks[i];
            }
        }
        return 0;
    }

    public synchronized void reset() {
        states.clear();
    }

    private static final class State {
        private final int[] windowBuckets;
        private final int[] ring;
        private final int[] sums;
        private final int[] peaks;
        private long lastIndex = Long.MIN_VALUE;

        State(int[] windows) {
            this.windowBuckets = new int[windows.length];
            for (int i = 0; i < windows.length; i++) {
                windowBuckets[i] = windows[i] * BUCKETS_PER_SECOND;
            }
            this.ring = new int[windowBuckets[windowBuckets.length - 1]];
            this.sums = new int[windows.length];
            this.peaks = new int[windows.length];
        }

        void record(long index) {
            advance(index);
            ring[slot(index)]++;
            for (int i = 0; i < sums.length; i++) {
                sums[i]++;
                peaks[i] = Math.max(peaks[i], sums[i]);
            }
        }

        private void advance(long index) {
            if (lastIndex == Long.MIN_VALUE || index - lastIndex >= ring.length) {
                Arrays.fill(ring, 0);
                Arrays.fill(sums, 0);
            } else {
                for (long step = lastIndex + 1; step <= index; step++) {
                    for (int i = 0; i < sums.length; i++) {
                        sums[i] -= ring[slot(step - windowBuckets[i])];
                    }
                    ring[slot(step)] = 0;
                }
            }
            lastIndex = Math.max(lastIndex, index);
        }

        private int slot(long index) {
            return (int) Math.floorMod(index, (long) ring.length);
        }
    }
}
