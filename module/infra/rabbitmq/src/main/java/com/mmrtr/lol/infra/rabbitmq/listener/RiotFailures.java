package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.exception.RiotServerException;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Riot 호출 실패를 나눈다. 갱신 단계·매치 적재 큐·추가 매치 찾기가 같은 규칙으로 다시 넣을지 정한다.
 * 다시 보내는 일은 하지 않는다 — 호출하는 쪽이 메시지 단위로 시도 수를 올려 다시 넣는다.
 */
public final class RiotFailures {

    private RiotFailures() {
    }

    /** cause 사슬에 429({@link RiotRateLimitException})·5xx·IO·시간 초과가 있으면 true. 다시 받으면 될 수 있는 실패다. */
    public static boolean isTransient(Throwable failure) {
        return causedBy(failure, RiotRateLimitException.class)
                || causedBy(failure, RiotServerException.class)
                || causedBy(failure, IOException.class)
                || causedBy(failure, TimeoutException.class);
    }

    /** cause 사슬의 Riot 예외가 429 가 아닌 4xx({@link RiotClientException}, 404 포함)면 true. 다시 받아도 같다. */
    public static boolean isClientError(Throwable failure) {
        RiotClientException client = findCause(failure, RiotClientException.class);
        return client != null
                && !(client instanceof RiotRateLimitException)
                && (client.getStatus() == null || client.getStatus().value() != 429);
    }

    /** cause 사슬(CompletionException 포함) 어딘가에 type 이 있는지 본다. */
    public static boolean causedBy(Throwable throwable, Class<? extends Throwable> type) {
        return findCause(throwable, type) != null;
    }

    /** cause 사슬(CompletionException 포함)에서 처음 나오는 type 예외. 없으면(throwable 이 null 이어도) null. */
    public static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = throwable; t != null && seen.add(t); t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }
}
