package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import com.mmrtr.lol.support.riot.RiotPermitDeniedException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 갱신 실패 예외를 {@code lol.renewal.completed} 의 reason 태그 값으로 바꾼다.
 * 태그 카디널리티를 막으려고 값은 고정된 집합만 돌려준다.
 */
public final class RenewalFailureReason {

    public static final String BUDGET_EXCEEDED = "budget_exceeded";
    public static final String RIOT_404 = "riot_404";
    public static final String RIOT_429 = "riot_429";
    public static final String RIOT_4XX = "riot_4xx";
    public static final String RIOT_5XX = "riot_5xx";
    public static final String TIMEOUT = "timeout";
    public static final String OTHER = "other";

    private RenewalFailureReason() {
    }

    public static String of(Throwable throwable) {
        Throwable t = unwrap(throwable);
        if (t instanceof RiotPermitDeniedException) {
            return BUDGET_EXCEEDED;
        }
        // 하위 클래스를 먼저 본다
        if (t instanceof RiotClientNotFoundException) {
            return RIOT_404;
        }
        if (t instanceof RiotClientException e) {
            return e.getStatus() != null && e.getStatus().value() == 429 ? RIOT_429 : RIOT_4XX;
        }
        if (t instanceof RiotServerException) {
            return RIOT_5XX;
        }
        if (causedByTimeout(throwable)) {
            return TIMEOUT;
        }
        return OTHER;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable t = throwable;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    /**
     * cause 사슬 어딘가에 시간 초과가 있는지 본다.
     * HTTP 읽기 시간 초과는 RestClient 가 {@code ResourceAccessException(HttpTimeoutException)} 으로 감싸 올린다.
     */
    private static boolean causedByTimeout(Throwable throwable) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = throwable; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof TimeoutException || t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
