package com.mmrtr.lol.support.riot;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 한 번에 받은 허가 묶음. 여러 스레드가 꺼내 써도 같은 허가를 두 번 주지 않는다.
 */
public final class RiotPermits {

    private final Map<RiotMethod, Queue<RiotPermit>> byMethod = new EnumMap<>(RiotMethod.class);

    private RiotPermits(Collection<RiotPermit> permits) {
        // 생성 후에는 맵 구조를 바꾸지 않는다. 큐만 동시에 변한다.
        for (RiotPermit permit : permits) {
            byMethod.computeIfAbsent(permit.method(), m -> new ConcurrentLinkedQueue<>()).add(permit);
        }
    }

    public static RiotPermits of(Collection<RiotPermit> permits) {
        return new RiotPermits(permits);
    }

    /** method 의 허가 하나를 꺼낸다. 남은 것이 없으면 IllegalStateException. */
    public RiotPermit take(RiotMethod method) {
        Queue<RiotPermit> queue = byMethod.get(method);
        RiotPermit permit = queue == null ? null : queue.poll();
        if (permit == null) {
            throw new IllegalStateException("남은 Riot 호출 허가가 없습니다: " + method);
        }
        return permit;
    }

    public int remaining(RiotMethod method) {
        Queue<RiotPermit> queue = byMethod.get(method);
        return queue == null ? 0 : queue.size();
    }
}
