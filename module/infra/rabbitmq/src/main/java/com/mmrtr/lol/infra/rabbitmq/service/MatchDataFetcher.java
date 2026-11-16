package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

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

            Set<String> existMatchIds = new HashSet<>(matchRepositoryPort.findExistingMatchIds(matchIds));
            List<String> newMatchIds = matchIds.stream().filter(matchId -> !existMatchIds.contains(matchId)).toList();
            return new FetchNewMatchIdsResult(newMatchIds, hasMoreMatches, dbRevisionDateSeconds);
        });
    }

    public CompletableFuture<List<MatchDto>> fetchMatchDetails(
            List<String> matchIds, Platform platform, Executor executor) {
        return fetchAll(matchIds, id -> matchApiPort.fetchMatchById(id, platform.name(), executor));
    }

    public CompletableFuture<List<TimelineDto>> fetchTimelines(
            List<String> matchIds, Platform platform, Executor executor) {
        return fetchAll(matchIds, id -> matchApiPort.fetchTimelineById(id, platform.name(), executor));
    }

    private <T> CompletableFuture<List<T>> fetchAll(
            List<String> matchIds, Function<String, CompletableFuture<T>> fetcher) {
        List<CompletableFuture<T>> futures = matchIds.stream().map(fetcher).toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }
}
