package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 갱신 실패 예외를 {@code lol.renewal.completed} 의 reason 태그 값으로 바꾼다.
 * 태그 카디널리티를 막으려고 값은 고정된 집합만 돌려준다.
 */
final class RenewalFailureReason {

    static final String BUDGET_EXCEEDED = "budget_exceeded";
    static final String RIOT_404 = "riot_404";
    static final String RIOT_429 = "riot_429";
    static final String RIOT_4XX = "riot_4xx";
    static final String RIOT_5XX = "riot_5xx";
    static final String TIMEOUT = "timeout";
    static final String OTHER = "other";

    private RenewalFailureReason() {
    }

    static String of(Throwable throwable) {
        Throwable t = unwrap(throwable);
        // 하위 클래스를 먼저 본다
        if (t instanceof RiotBudgetExceededException) {
            return BUDGET_EXCEEDED;
        }
        if (t instanceof RiotClientNotFoundException) {
            return RIOT_404;
        }
        if (t instanceof RiotClientException e) {
            return e.getStatus() != null && e.getStatus().value() == 429 ? RIOT_429 : RIOT_4XX;
        }
        if (t instanceof RiotServerException) {
            return RIOT_5XX;
        }
        if (t instanceof TimeoutException) {
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
}
