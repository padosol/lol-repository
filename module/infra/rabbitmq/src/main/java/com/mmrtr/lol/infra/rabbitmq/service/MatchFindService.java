package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
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
    private final Executor matchFindExecutor;

    /** 추가 매치 찾기는 백그라운드 일이라 Riot 호출을 BACKGROUND 허가로 받는다. */
    public void process(SummonerRenewalMessage summonerRenewalMessage) {
        String puuid = summonerRenewalMessage.puuid();
        Platform platform = Platform.valueOfName(summonerRenewalMessage.platform());
        long revisionDate = Math.max(summonerRenewalMessage.revisionDate(), 1767225600L);

        log.info("Starting renewal match ID search for puuid: {} on platform: {}", puuid, platform);

        List<String> allMatchIds = matchIdCollector
                .collectAll(puuid, platform.name(), revisionDate, summonerRenewalMessage.startOffsetOrDefault(),
                        RiotCallPriority.BACKGROUND, matchFindExecutor);

        if (allMatchIds.isEmpty()) {
            log.info("No match IDs fetched for puuid: {}. Nothing to process.", puuid);
            return;
        }

        int sentCount = matchIdPublisher.publishNewMatchIds(allMatchIds, platform.name());

        log.info("Found {} total matches, {} sent after dedup.", allMatchIds.size(), sentCount);
    }
}
