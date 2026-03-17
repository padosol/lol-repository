package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.repository.MatchRepositoryPort;
import com.mmrtr.lol.domain.match.service.port.MatchApiPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchDataFetcher {

    private static final int MATCH_FETCH_COUNT = 20;

    private final MatchApiPort matchApiPort;
    private final MatchRepositoryPort matchRepositoryPort;

    public record FetchNewMatchIdsResult(
            List<String> newMatchIds,
            boolean hasMoreMatches,
            long dbRevisionDateSeconds
    ) {}

    public CompletableFuture<FetchNewMatchIdsResult> fetchNewMatchIds(
            String puuid, Platform platform, long dbRevisionDateSeconds, Executor executor) {

        CompletableFuture<List<String>> matchIdListFuture = matchApiPort.fetchMatchIdsByPuuid(
                puuid, platform.name(), dbRevisionDateSeconds, 0, MATCH_FETCH_COUNT, executor);

        return matchIdListFuture.thenApply(matchIds -> {
            if (matchIds == null || matchIds.isEmpty()) {
                return new FetchNewMatchIdsResult(List.of(), false, dbRevisionDateSeconds);
            }

            boolean hasMoreMatches = matchIds.size() == MATCH_FETCH_COUNT;
            if (hasMoreMatches) {
                log.debug("matchIds size is 20. more matchIds will be searched after renewal completes");
            }

            List<String> existMatchIds = matchRepositoryPort.findExistingMatchIds(matchIds);
            List<String> newMatchIds = matchIds.stream().filter(matchId -> !existMatchIds.contains(matchId)).toList();
            return new FetchNewMatchIdsResult(newMatchIds, hasMoreMatches, dbRevisionDateSeconds);
        });
    }

    public CompletableFuture<List<MatchDto>> fetchMatchDetails(
            List<String> matchIds, Platform platform, Executor executor) {

        List<CompletableFuture<MatchDto>> matchAllOfFuture = matchIds.stream()
                .map(matchId -> matchApiPort.fetchMatchById(matchId, platform.name(), executor))
                .toList();

        CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(matchAllOfFuture.toArray(new CompletableFuture[0]));

        return allOfFuture.thenApply(v -> matchAllOfFuture.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    public CompletableFuture<List<TimelineDto>> fetchTimelines(
            List<String> matchIds, Platform platform, Executor executor) {

        List<CompletableFuture<TimelineDto>> timelineAllOfFuture = matchIds.stream()
                .map(matchId -> matchApiPort.fetchTimelineById(matchId, platform.name(), executor))
                .toList();

        CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(timelineAllOfFuture.toArray(new CompletableFuture[0]));

        return allOfFuture.thenApply(v -> timelineAllOfFuture.stream()
                .map(CompletableFuture::join)
                .toList());
    }
}
