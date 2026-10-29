package com.mmrtr.lol.support.riot;

import java.util.function.Supplier;

/**
 * 현재 스레드의 Riot 호출 우선순위를 담는 스코프. 다른 스레드로 전파되지 않는다.
 */
public final class RiotCallScope {

    private static final ThreadLocal<RiotCallPriority> CURRENT = new ThreadLocal<>();

    private RiotCallScope() {
    }

    /** 스코프 밖이면 USER. */
    public static RiotCallPriority current() {
        RiotCallPriority priority = CURRENT.get();
        return priority != null ? priority : RiotCallPriority.USER;
    }

    public static <T> T call(RiotCallPriority priority, Supplier<T> body) {
        RiotCallPriority previous = CURRENT.get();
        CURRENT.set(priority);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void run(RiotCallPriority priority, Runnable body) {
        call(priority, () -> {
            body.run();
            return null;
        });
    }
}
