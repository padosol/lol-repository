package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchFindService {

    private final MatchRepositoryPort matchRepositoryPort;
    private final MatchRedisService matchRedisService;
    private final MessageSender messageSender;
    private final MatchApiPort matchApiPort;
    private final Executor matchFindExecutor;

    public void process(SummonerRenewalMessage summonerRenewalMessage) {
        String puuid = summonerRenewalMessage.puuid();
        Platform platform = Platform.valueOfName(summonerRenewalMessage.platform());
        long revisionDate = Math.max(summonerRenewalMessage.revisionDate(), 1767225600L);

        log.info("Starting renewal match ID search for puuid: {} on platform: {}", puuid, platform);

        List<String> allFetchedMatchIds = fetchAllMatchIds(puuid, platform, revisionDate);

        if (allFetchedMatchIds.isEmpty()) {
            log.info("No match IDs fetched for puuid: {}. Nothing to process.", puuid);
            return;
        }

        List<String> newMatchIds = filterExistingMatchIds(allFetchedMatchIds);
        int sentCount = publishNewMatches(newMatchIds, platform);

        log.info("Found {} total matches, {} new, {} sent after dedup.",
                allFetchedMatchIds.size(), newMatchIds.size(), sentCount);
    }

    private List<String> fetchAllMatchIds(String puuid, Platform platform, long revisionDate) {
        List<String> allFetchedMatchIds = new ArrayList<>();
        int offset = 20;
        int count = 100;

        int retry = 0;
        boolean hasMoreMatches = true;
        while (retry < 10 && hasMoreMatches) {
            List<String> fetchedMatchIds = matchApiPort
                    .fetchMatchIdsByPuuid(puuid, platform.name(), revisionDate, offset, count, matchFindExecutor)
                    .join();

            if (fetchedMatchIds == null || fetchedMatchIds.isEmpty()) {
                hasMoreMatches = false;
            } else {
                allFetchedMatchIds.addAll(fetchedMatchIds);

                if (fetchedMatchIds.size() < count) {
                    hasMoreMatches = false;
                } else {
                    offset += count;
                    retry += 1;
                }
            }
        }
        return allFetchedMatchIds;
    }

    private List<String> filterExistingMatchIds(List<String> allFetchedMatchIds) {
        List<String> existingMatchIds = matchRepositoryPort.findExistingMatchIds(allFetchedMatchIds);
        Set<String> existingMatchIdSet = new HashSet<>(existingMatchIds);

        return allFetchedMatchIds.stream()
                .filter(matchId -> !existingMatchIdSet.contains(matchId))
                .toList();
    }

    private int publishNewMatches(List<String> newMatchIds, Platform platform) {
        int sentCount = 0;
        for (String matchId : newMatchIds) {
            if (matchRedisService.tryMarkPending(matchId)) {
                messageSender.sendMessageByMatchId(matchId, platform.name());
                sentCount++;
            }
        }
        return sentCount;
    }
}
