package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchFindService {

    private static final int START_OFFSET = 20;

    private final MatchIdCollector matchIdCollector;
    private final MatchIdPublisher matchIdPublisher;
    private final Executor matchFindExecutor;

    public void process(SummonerRenewalMessage summonerRenewalMessage) {
        String puuid = summonerRenewalMessage.puuid();
        Platform platform = Platform.valueOfName(summonerRenewalMessage.platform());
        long revisionDate = Math.max(summonerRenewalMessage.revisionDate(), 1767225600L);

        log.info("Starting renewal match ID search for puuid: {} on platform: {}", puuid, platform);

        List<String> allMatchIds = matchIdCollector
                .collectAll(puuid, platform.name(), revisionDate, START_OFFSET, matchFindExecutor);

        if (allMatchIds.isEmpty()) {
            log.info("No match IDs fetched for puuid: {}. Nothing to process.", puuid);
            return;
        }

        int sentCount = matchIdPublisher.publishNewMatchIds(allMatchIds, platform.name());

        log.info("Found {} total matches, {} sent after dedup.", allMatchIds.size(), sentCount);
    }
}
