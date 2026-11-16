package com.mmrtr.lol.support.riot;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Riot API 호출 허가를 내주는 포트. 호출하는 쪽이 호출 전에 허가를 받는다.
 */
public interface RiotPermitPort {

    /**
     * counts 만큼의 허가를 한 번에 요청한다. 하나라도 거절되면 빈 값이다.
     */
    Optional<RiotPermits> tryAcquire(String platform, RiotCallPriority priority,
                                     Map<RiotMethod, Integer> counts);

    /**
     * 거절이면 pause 만큼 자고 다시 묻는다. attempts 번 모두 거절이면 빈 값이다.
     * 인터럽트되면 플래그를 되살리고 빈 값을 돌려준다. 스케줄러 스레드 전용이다.
     */
    default Optional<RiotPermits> acquireWithRetry(String platform, RiotCallPriority priority,
                                                   Map<RiotMethod, Integer> counts,
                                                   int attempts, Duration pause) {
        for (int i = 0; i < attempts; i++) {
            if (i > 0 && !pause.isZero()) {
                try {
                    Thread.sleep(pause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
            Optional<RiotPermits> granted = tryAcquire(platform, priority, counts);
            if (granted.isPresent()) {
                return granted;
            }
        }
        return Optional.empty();
    }
}
