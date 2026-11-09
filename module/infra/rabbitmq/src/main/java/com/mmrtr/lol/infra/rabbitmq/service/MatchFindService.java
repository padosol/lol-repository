package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.listener.RenewalFailureReason;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalStepPublisher;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchFindService {

    /** 일시 오류의 시도 수 상한. 다음 시도 수가 이 값에 닿으면 다시 넣지 않고 버린다. */
    static final int MAX_ATTEMPT = 2;

    private final MatchIdCollector matchIdCollector;
    private final MatchIdPublisher matchIdPublisher;
    private final RenewalStepPublisher stepPublisher;
    private final Executor matchFindExecutor;
    private final MeterRegistry meterRegistry;

    /**
     * 추가 매치 찾기는 백그라운드 일이라 Riot 호출을 BACKGROUND 허가로 받는다.
     * 허가가 거절되거나 조회가 일시 오류면 그 페이지에서 멈추고, 모은 id 는 발행한 뒤 멈춘 offset 부터 이어서 찾는 메시지를
     * 잠시 뒤 다시 넣는다. 거절이면 시도 수를 그대로 두고, 일시 오류면 하나 올린다. 다음 시도 수가 {@link #MAX_ATTEMPT} 에
     * 닿으면 다시 넣지 않고 {@code lol.match.find.dropped{reason}} 으로 센다.
     */
    public void process(SummonerRenewalMessage summonerRenewalMessage) {
        String puuid = summonerRenewalMessage.puuid();
        Platform platform = Platform.valueOfName(summonerRenewalMessage.platform());
        long revisionDate = Math.max(summonerRenewalMessage.revisionDate(), 1767225600L);

        log.info("Starting renewal match ID search for puuid: {} on platform: {}", puuid, platform);

        MatchIdCollector.Collected collected = matchIdCollector
                .collectAll(puuid, platform.name(), revisionDate, summonerRenewalMessage.startOffsetOrDefault(),
                        RiotCallPriority.BACKGROUND, matchFindExecutor);
        List<String> allMatchIds = collected.ids();

        if (allMatchIds.isEmpty()) {
            log.info("No match IDs fetched for puuid: {}. Nothing to process.", puuid);
        } else {
            int sentCount = matchIdPublisher.publishNewMatchIds(allMatchIds, platform.name());
            log.info("Found {} total matches, {} sent after dedup.", allMatchIds.size(), sentCount);
        }

        collected.stop().ifPresent(stop -> continueLater(summonerRenewalMessage, stop));
    }

    private void continueLater(SummonerRenewalMessage message, MatchIdCollector.Stop stop) {
        String puuid = message.puuid();
        if (stop.reason() == MatchIdCollector.StopReason.DENIED) {
            log.warn("Riot permit denied while finding matches, requeueing from offset {}. puuid={}",
                    stop.offset(), puuid);
            stepPublisher.requeueMatchFindLater(message.fromOffset(stop.offset()));
            return;
        }
        if (message.attempt() + 1 < MAX_ATTEMPT) {
            log.warn("Transient failure while finding matches, requeueing from offset {} with attempt {}. puuid={}",
                    stop.offset(), message.attempt() + 1, puuid);
            stepPublisher.requeueMatchFindLater(message.nextAttempt(stop.offset()));
            return;
        }
        log.warn("Dropping match find message after retries, puuid={}, offset={}: {}",
                puuid, stop.offset(), String.valueOf(stop.cause()));
        meterRegistry.counter("lol.match.find.dropped", "reason", RenewalFailureReason.of(stop.cause())).increment();
    }
}
