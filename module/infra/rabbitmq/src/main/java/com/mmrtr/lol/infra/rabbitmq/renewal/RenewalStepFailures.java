package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.infra.rabbitmq.listener.RenewalFailureReason;
import com.mmrtr.lol.infra.rabbitmq.listener.RiotFailures;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 갱신 단계(①~④)가 함께 쓰는 낡은 메시지 판정, 실패 분류와 단계 지표({@code lol.renewal.step{step, result}}).
 * {@link #isCurrent}·{@link #fail}·{@link #fetchFailed} 는 안에서 부르는 레코드 조회·실패 표시·재발행·
 * {@link RenewalFinisher} 가 던지는 예외를 잡아 로그와 {@code failed} 지표로 남기고 밖으로 던지지 않는다.
 * 그래서 단계는 이 헬퍼만 거쳐 끝내면 리스너가 정상 반환한다. 지표 등록({@code count}) 자체의 실패는 막지 않는다.
 */
@Slf4j
final class RenewalStepFailures {

    static final int MAX_ATTEMPT = 2;

    private static final String STEP_METRIC = "lol.renewal.step";

    private final String step;
    private final RenewalRecordPort recordPort;
    private final RenewalFinisher finisher;
    private final MeterRegistry meterRegistry;

    RenewalStepFailures(String step, RenewalRecordPort recordPort, RenewalFinisher finisher,
                        MeterRegistry meterRegistry) {
        this.step = step;
        this.recordPort = recordPort;
        this.finisher = finisher;
        this.meterRegistry = meterRegistry;
    }

    void success() {
        count("success");
    }

    /**
     * 이 메시지가 레코드의 갱신 것이면 true. 레코드가 없거나 renewalId 가 다르면 {@code skipped/stale} 로 세고 false.
     * 레코드 조회가 던지면 WARN 과 {@code failed} 를 남기고 false 다 (메시지는 버리고, 그 갱신은 기한으로 끝난다).
     */
    boolean isCurrent(String puuid, String renewalId) {
        RenewalRecord record;
        try {
            record = recordPort.find(puuid).orElse(null);
        } catch (Exception e) {
            log.warn("갱신 레코드 조회 실패. 메시지를 버린다. step={}, puuid={}, renewalId={}", step, puuid, renewalId, e);
            count("failed");
            return false;
        }
        if (record != null && record.isFor(renewalId)) {
            return true;
        }
        stale(puuid, renewalId);
        return false;
    }

    private void stale(String puuid, String renewalId) {
        log.info("낡은 갱신 메시지를 건너뜀. step={}, puuid={}, renewalId={}", step, puuid, renewalId);
        try {
            finisher.skipped("stale");
        } catch (Exception e) {
            log.warn("낡은 갱신 메시지 기록 실패. step={}, puuid={}, renewalId={}", step, puuid, renewalId, e);
            count("failed");
            return;
        }
        count("skipped");
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
     * 허가가 거절됐다. 시도 수를 올리지 않고 잠시 뒤 다시 넣는다 ({@code requeue}).
     * 다시 넣기가 던지면 그 갱신을 실패시킨다.
     */
    void denied(String puuid, String renewalId, Runnable requeueLater) {
        try {
            count("requeue");
            requeueLater.run();
        } catch (Exception failure) {
            log.error("{} 단계 다시 넣기 실패. puuid={}", step, puuid, failure);
            fail(puuid, renewalId, failure);
        }
    }

    /**
     * Riot 조회 실패를 분류한다. 404 는 not_found. 429·5xx·IO·시간 초과({@link RiotFailures#isTransient})는
     * {@link #MAX_ATTEMPT} 번째 시도 전이면 시도 수를 올려 다시 넣고 그 뒤엔 예외에서 reason 을 뽑아 실패시킨다.
     * 그 밖은 바로 실패시킨다.
     */
    void fetchFailed(String puuid, String renewalId, int attempt, Exception e, Runnable requeueNextAttempt) {
        try {
            if (RiotFailures.causedBy(e, RiotClientNotFoundException.class)) {
                fail(puuid, renewalId, "not_found");
            } else if (RiotFailures.isTransient(e)) {
                if (attempt < MAX_ATTEMPT) {
                    count("requeue");
                    requeueNextAttempt.run();
                } else {
                    fail(puuid, renewalId, e);
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
}
