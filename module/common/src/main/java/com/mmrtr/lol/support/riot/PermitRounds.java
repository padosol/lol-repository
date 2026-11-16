package com.mmrtr.lol.support.riot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 항목들의 허가를 묶음으로 받되, 거절되면 크기를 1개까지 절반씩 줄여 다시 묻는다.
 */
public final class PermitRounds {

    private PermitRounds() {
    }

    /**
     * 허가를 받은 묶음마다 onGrant 를 부르고, 끝내 허가받지 못한 항목을 원래 순서로 돌려준다.
     * 시도 크기는 sizes(min(남은 수, maxSize)) 이며 시도 사이에 기다리지 않는다.
     */
    public static <T> List<T> acquire(RiotPermitPort port, String platform, RiotCallPriority priority,
                                      List<T> items, int maxSize,
                                      Function<T, Map<RiotMethod, Integer>> costOf,
                                      BiConsumer<List<T>, RiotPermits> onGrant) {
        List<T> remaining = items;
        while (!remaining.isEmpty()) {
            boolean granted = false;
            for (int size : sizes(Math.min(remaining.size(), maxSize))) {
                List<T> chunk = remaining.subList(0, size);
                Optional<RiotPermits> permits = port.tryAcquire(platform, priority, totalCost(chunk, costOf));
                if (permits.isPresent()) {
                    List<T> grantedChunk = List.copyOf(chunk);
                    remaining = List.copyOf(remaining.subList(size, remaining.size()));
                    onGrant.accept(grantedChunk, permits.get());
                    granted = true;
                    break;
                }
            }
            if (!granted) {
                return List.copyOf(remaining);
            }
        }
        return List.of();
    }

    /** n, ceil(n/2), ceil(n/4) … 1 로 절반(올림)씩 줄인 시도 크기. 최소 1. */
    public static List<Integer> sizes(int n) {
        List<Integer> sizes = new ArrayList<>();
        int size = Math.max(n, 1);
        sizes.add(size);
        while (size > 1) {
            size = (size + 1) / 2;
            sizes.add(size);
        }
        return sizes;
    }

    private static <T> Map<RiotMethod, Integer> totalCost(
            List<T> chunk, Function<T, Map<RiotMethod, Integer>> costOf) {
        Map<RiotMethod, Integer> total = new EnumMap<>(RiotMethod.class);
        for (T item : chunk) {
            costOf.apply(item).forEach((method, count) -> total.merge(method, count, Integer::sum));
        }
        return total;
    }
}
