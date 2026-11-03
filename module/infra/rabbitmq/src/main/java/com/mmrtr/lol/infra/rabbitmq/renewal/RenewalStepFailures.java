package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.infra.rabbitmq.listener.RenewalFailureReason;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * 갱신 단계(①~④)가 함께 쓰는 실패 분류와 단계 지표({@code lol.renewal.step{step, result}}).
 * 어떤 경우에도 예외를 밖으로 던지지 않아 리스너가 정상 반환하도록 돕는다.
 */
@Slf4j
final class RenewalStepFailures {

    static final int MAX_RATE_LIMIT_ATTEMPT = 2;

    private static final String STEP_METRIC = "lol.renewal.step";

    private final String step;
    private final RenewalFinisher finisher;
    private final MeterRegistry meterRegistry;

    RenewalStepFailures(String step, RenewalFinisher finisher, MeterRegistry meterRegistry) {
        this.step = step;
        this.finisher = finisher;
        this.meterRegistry = meterRegistry;
    }

    void success() {
        count("success");
    }

    void stale(String puuid, String renewalId) {
        log.info("낡은 갱신 메시지를 건너뜀. step={}, puuid={}, renewalId={}", step, puuid, renewalId);
        count("skipped");
        finisher.skipped("stale");
    }

    /** 레코드를 FAILED 로 바꾼다. 실패 표시마저 던지면 메시지가 다시 돌기만 하므로 로그만 남긴다. */
    void fail(String puuid, String renewalId, String reason) {
        count("failed");
        try {
            finisher.fail(puuid, renewalId, reason);
        } catch (Exception failure) {
            log.error("갱신 실패 표시에 실패. step={}, puuid={}, renewalId={}", step, puuid, renewalId, failure);
        }
    }

    void fail(String puuid, String renewalId, Exception cause) {
        fail(puuid, renewalId, RenewalFailureReason.of(cause));
    }

    /**
     * Riot 조회 실패를 분류한다. 404 는 not_found, 429 는 두 번까지 시도 수를 올려 다시 넣고 그 뒤엔 riot_429,
     * 예약 실패는 시도 수를 올리지 않고 잠시 뒤 다시 넣고, 그 밖은 예외에서 reason 을 뽑는다.
     */
    void fetchFailed(String puuid, String renewalId, int attempt, Exception e,
                     Runnable requeueNextAttempt, Runnable requeueLater) {
        try {
            if (causedBy(e, RiotClientNotFoundException.class)) {
                fail(puuid, renewalId, "not_found");
            } else if (causedBy(e, RiotBudgetExceededException.class)) {
                count("requeue");
                requeueLater.run();
            } else if (causedBy(e, RiotRateLimitException.class)) {
                if (attempt < MAX_RATE_LIMIT_ATTEMPT) {
                    count("requeue");
                    requeueNextAttempt.run();
                } else {
                    fail(puuid, renewalId, RenewalFailureReason.RIOT_429);
                }
            } else {
                log.error("{} 단계 조회 실패. puuid={}", step, puuid, e);
                fail(puuid, renewalId, e);
            }
        } catch (Exception failure) {
            log.error("{} 단계 실패 처리 중 예외. puuid={}", step, puuid, failure);
            fail(puuid, renewalId, failure);
        }
    }

    private void count(String result) {
        meterRegistry.counter(STEP_METRIC, "step", step, "result", result).increment();
    }

    private static boolean causedBy(Throwable throwable, Class<? extends Throwable> type) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = throwable; t != null && seen.add(t); t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
