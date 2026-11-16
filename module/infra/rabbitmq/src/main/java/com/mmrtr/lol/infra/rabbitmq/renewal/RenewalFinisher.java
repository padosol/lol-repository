package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 갱신 레코드가 끝 상태로 바뀐 직후 한 번 해야 하는 뒷일(커서·마지막 호출 시각·지표)을 모은다. */
@Component
public class RenewalFinisher {

    private static final String COMPLETED = "lol.renewal.completed";
    private static final String DURATION = "lol.renewal.duration";
    private static final String LATE_COMPLETED = "lol.renewal.late_completed";

    private final RenewalRecordPort recordPort;
    private final SummonerRepositoryPort summonerRepositoryPort;
    private final MeterRegistry meterRegistry;
    private final LongSupplier clock;

    @Autowired
    public RenewalFinisher(RenewalRecordPort recordPort, SummonerRepositoryPort summonerRepositoryPort,
                           MeterRegistry meterRegistry) {
        this(recordPort, summonerRepositoryPort, meterRegistry, System::currentTimeMillis);
    }

    RenewalFinisher(RenewalRecordPort recordPort, SummonerRepositoryPort summonerRepositoryPort,
                    MeterRegistry meterRegistry, LongSupplier clock) {
        this.recordPort = recordPort;
        this.summonerRepositoryPort = summonerRepositoryPort;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public void fail(String puuid, String renewalId, String reason) {
        finish(puuid, recordPort.fail(puuid, renewalId, reason));
    }

    public void skipped(String reason) {
        meterRegistry.counter(COMPLETED, "result", "skipped", "reason", reason).increment();
    }

    public void finish(String puuid, RenewalTransition transition) {
        if (transition == null || transition == RenewalTransition.NONE) {
            return;
        }
        RenewalRecord record = recordPort.find(puuid).orElse(null);
        if (record == null) {
            return;
        }
        switch (transition) {
            case SUCCEEDED -> succeeded(puuid, record);
            case FAILED -> failed(record);
            case FAILED_LATE_COMPLETED -> {
                failed(record);
                lateCompleted(puuid, record);
            }
            case LATE_COMPLETED -> lateCompleted(puuid, record);
            default -> { }
        }
    }

    private void succeeded(String puuid, RenewalRecord record) {
        moveCursor(puuid, record);
        summonerRepositoryPort.updateLastRiotCallDate(puuid);
        meterRegistry.counter(COMPLETED, "result", "success", "reason", "none").increment();
        recordDuration("success", record);
    }

    private void failed(RenewalRecord record) {
        String reason = record.reason() == null ? "other" : record.reason();
        meterRegistry.counter(COMPLETED, "result", "failed", "reason", reason).increment();
        recordDuration("failed", record);
    }

    private void lateCompleted(String puuid, RenewalRecord record) {
        moveCursor(puuid, record);
        meterRegistry.counter(LATE_COMPLETED).increment();
    }

    private void moveCursor(String puuid, RenewalRecord record) {
        if (record.riotRevisionDate() == null) {
            return;
        }
        LocalDateTime revisionDate = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(record.riotRevisionDate()), ZoneId.systemDefault());
        summonerRepositoryPort.updateRevisionDate(puuid, revisionDate);
    }

    private void recordDuration(String result, RenewalRecord record) {
        Timer.builder(DURATION).tag("result", result).register(meterRegistry)
                .record(Duration.ofMillis(Math.max(0, clock.getAsLong() - record.acceptedAt())));
    }
}
