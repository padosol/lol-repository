package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalStepPublisher;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchFindService {

    private final MatchIdCollector matchIdCollector;
    private final MatchIdPublisher matchIdPublisher;
    private final RenewalStepPublisher stepPublisher;
    private final Executor matchFindExecutor;

    /**
     * 추가 매치 찾기는 백그라운드 일이라 Riot 호출을 BACKGROUND 허가로 받는다.
     * 허가가 거절되면 그 페이지에서 멈추고, 모은 id 는 발행한 뒤 거절된 offset 부터 이어서 찾는 메시지를 잠시 뒤 다시 넣는다.
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

        collected.deniedAtOffset().ifPresent(offset -> {
            log.warn("Riot permit denied while finding matches, requeueing from offset {}. puuid={}", offset, puuid);
            stepPublisher.requeueMatchFindLater(new SummonerRenewalMessage(
                    puuid, summonerRenewalMessage.platform(), summonerRenewalMessage.revisionDate(), offset));
        });
    }
}
