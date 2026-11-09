package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.infra.redis.service.RenewalMatchClaimService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 단계 리스너가 스스로 끝내지 못하는 갱신을 끝낸다(스펙 5.5). 다른 갱신이 선점했거나 매치 적재 큐로 넘긴 대상이
 * DB 에 들어오면 매치 비트를 켜고, 접수 뒤 실패 표시가 남은 대상이 있으면 match_failed 로, 기한이 지나면 기한
 * 실패로 끝낸다. 기한 FAILED 레코드도 대상이 뒤늦게 들어오면 늦은 완료로 닫는다.
 */
@Slf4j
@Component
public class RenewalCompletionJudge {

    static final String MATCH_FAILED = "match_failed";

    private final RenewalRecordPort recordPort;
    private final MatchRepositoryPort matchRepositoryPort;
    private final RenewalMatchClaimService claimService;
    private final RenewalFinisher finisher;
    private final AtomicInteger activeCount = new AtomicInteger();

    public RenewalCompletionJudge(RenewalRecordPort recordPort, MatchRepositoryPort matchRepositoryPort,
                                  RenewalMatchClaimService claimService, RenewalFinisher finisher,
                                  MeterRegistry meterRegistry) {
        this.recordPort = recordPort;
        this.matchRepositoryPort = matchRepositoryPort;
        this.claimService = claimService;
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

        Map<String, RenewalRecord> records = new LinkedHashMap<>();
        for (String puuid : puuids) {
            try {
                RenewalRecord record = recordPort.find(puuid).orElse(null);
                if (record == null) {
                    recordPort.deactivate(puuid);
                } else {
                    records.put(puuid, record);
                }
            } catch (Exception e) {
                log.warn("갱신 레코드 조회 실패 puuid={}", puuid, e);
            }
        }

        Map<String, Set<String>> targetsByPuuid = new HashMap<>();
        Set<String> allTargets = new HashSet<>();
        records.forEach((puuid, record) -> {
            if (!isMatchCandidate(record)) {
                return;
            }
            Set<String> targets = record.targets();
            targetsByPuuid.put(puuid, targets);
            allTargets.addAll(targets);
        });

        Set<String> existing = Set.of();
        Map<String, Long> failedAt = Map.of();
        if (!allTargets.isEmpty()) {
            List<String> ids = new ArrayList<>(allTargets);
            existing = new HashSet<>(matchRepositoryPort.findExistingMatchIds(ids));
            failedAt = claimService.findFailedAt(ids);
        }

        for (Map.Entry<String, RenewalRecord> entry : records.entrySet()) {
            String puuid = entry.getKey();
            try {
                judge(puuid, entry.getValue(), targetsByPuuid.get(puuid), existing, failedAt);
            } catch (Exception e) {
                log.warn("갱신 완료 판정 실패 puuid={}", puuid, e);
            }
        }
    }

    private void judge(String puuid, RenewalRecord record, Set<String> targets,
                       Set<String> existing, Map<String, Long> failedAt) {
        boolean progress = record.status() == RenewalStatus.PROGRESS;
        if (targets != null) {
            if (progress && hasFailedAfterAccepted(record, targets, failedAt)) {
                finisher.finish(puuid, recordPort.fail(puuid, record.renewalId(), MATCH_FAILED));
                return;
            }
            if (existing.containsAll(targets)) {
                finisher.finish(puuid, recordPort.markSteps(puuid, record.renewalId(),
                        EnumSet.of(RenewalStep.MATCH)));
                return;
            }
        }
        if (progress) {
            finisher.finish(puuid, recordPort.failIfOverdue(puuid, record.renewalId()));
        }
    }

    private boolean isMatchCandidate(RenewalRecord record) {
        boolean open = record.status() == RenewalStatus.PROGRESS || record.isDeadlineFailure();
        return open && record.targetsReady() && !record.has(RenewalStep.MATCH);
    }

    private boolean hasFailedAfterAccepted(RenewalRecord record, Set<String> targets, Map<String, Long> failedAt) {
        return targets.stream().anyMatch(id -> {
            Long at = failedAt.get(id);
            return at != null && at >= record.acceptedAt();
        });
    }
}
