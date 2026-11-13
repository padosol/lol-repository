package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기한이 지난 진행 중 갱신을 기한 실패로 끝낸다. 매치 단계는 스스로 매치 비트를 켜므로 대상을 기다리지 않는다.
 * 기한 실패 뒤 단계 비트가 다 켜지면 늦은 완료는 단계 비트({@code renewal-mark.lua})가 닫는다.
 * 레코드가 만료된 갱신은 활성 목록에서 뺀다.
 */
@Slf4j
@Component
public class RenewalCompletionJudge {

    private final RenewalRecordPort recordPort;
    private final RenewalFinisher finisher;
    private final AtomicInteger activeCount = new AtomicInteger();

    public RenewalCompletionJudge(RenewalRecordPort recordPort, RenewalFinisher finisher,
                                  MeterRegistry meterRegistry) {
        this.recordPort = recordPort;
        this.finisher = finisher;
        Gauge.builder("lol.renewal.active", activeCount, AtomicInteger::get).register(meterRegistry);
    }

    @Scheduled(fixedDelay = 500)
    public void sweep() {
        try {
            doSweep();
        } catch (Exception e) {
            log.warn("갱신 완료 판정 sweep 실패", e);
        }
    }

    private void doSweep() {
        List<String> puuids = recordPort.findActive();
        activeCount.set(puuids.size());
        for (String puuid : puuids) {
            try {
                judge(puuid);
            } catch (Exception e) {
                log.warn("갱신 완료 판정 실패 puuid={}", puuid, e);
            }
        }
    }

    private void judge(String puuid) {
        RenewalRecord record = recordPort.find(puuid).orElse(null);
        if (record == null) {
            recordPort.deactivate(puuid);
            return;
        }
        if (record.status() == RenewalStatus.PROGRESS) {
            finisher.finish(puuid, recordPort.failIfOverdue(puuid, record.renewalId()));
        }
    }
}
