package com.mmrtr.lol.support.riot;

import java.util.function.Supplier;

/**
 * 현재 스레드의 Riot 429 인라인 재시도 여부를 담는 스코프. 다른 스레드로 전파되지 않는다.
 * 스코프 안의 호출은 429 를 받자마자 예외로 올라가고, 재시도는 호출한 쪽이 정한다.
 */
public final class RiotRetryScope {

    private static final ThreadLocal<Boolean> RATE_LIMIT_RETRY = new ThreadLocal<>();

    private RiotRetryScope() {
    }

    /** 스코프 밖이면 true. */
    public static boolean rateLimitRetryEnabled() {
        Boolean enabled = RATE_LIMIT_RETRY.get();
        return enabled == null || enabled;
    }

    public static <T> T callWithoutRateLimitRetry(Supplier<T> body) {
        Boolean previous = RATE_LIMIT_RETRY.get();
        RATE_LIMIT_RETRY.set(Boolean.FALSE);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                RATE_LIMIT_RETRY.remove();
            } else {
                RATE_LIMIT_RETRY.set(previous);
            }
        }
    }

    public static void runWithoutRateLimitRetry(Runnable body) {
        callWithoutRateLimitRetry(() -> {
            body.run();
            return null;
        });
    }
}
