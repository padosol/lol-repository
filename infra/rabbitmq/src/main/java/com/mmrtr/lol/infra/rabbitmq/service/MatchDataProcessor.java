package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchCacheWritePort;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchDataProcessor {

    private static final int FETCH_TIMEOUT_SECONDS = 30;

    private final MatchApiPort matchApiPort;
    private final MatchRedisService matchRedisService;
    private final MatchBatchProcessor matchBatchProcessor;
    private final MatchCacheWritePort matchCacheWritePort;
    private final Executor riotApiExecutor;
    private final RRateLimiter globalApiRateLimiter;

    public enum Result {
        DUPLICATE,
        SUCCESS,
        FAILURE,
        QUEUE_FULL
    }

    public Result process(String matchId, String platformName) {
        if (!matchRedisService.tryMarkProcessing(matchId)) {
            log.debug("Skipping duplicate matchId: {}", matchId);
            return Result.DUPLICATE;
        }

        globalApiRateLimiter.acquire(1);

        CompletableFuture<MatchDto> matchFuture = matchApiPort
                .fetchMatchById(matchId, platformName, riotApiExecutor);
        CompletableFuture<TimelineDto> timelineFuture = matchApiPort
                .fetchTimelineById(matchId, platformName, riotApiExecutor);

        Pair<MatchDto, TimelineDto> dtoPair;
        try {
            dtoPair = CompletableFuture.allOf(matchFuture, timelineFuture)
                    .thenApply(v -> Pair.of(matchFuture.join(), timelineFuture.join()))
                    .get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout fetching match data for matchId {}", matchId);
            return Result.FAILURE;
        } catch (InterruptedException e) {
            log.warn("Interrupted while fetching match data for matchId {}", matchId);
            Thread.currentThread().interrupt();
            return Result.FAILURE;
        } catch (ExecutionException e) {
            log.warn("Failed to fetch data for matchId {}: {}", matchId, e.getCause().getMessage());
            return Result.FAILURE;
        }

        // 매치 데이터를 Redis 에 write-through (best-effort). DB insert 전에 호출하여
        // lol-server 가 최신 매치를 즉시 조회할 수 있게 한다. 어댑터 내부에서 예외를 swallow 하므로
        // 처리 흐름엔 영향이 없다.
        matchCacheWritePort.writeMatch(dtoPair.getFirst());

        if (!matchBatchProcessor.add(dtoPair)) {
            log.warn("Match batch queue is full, requeueing matchId {}", matchId);
            return Result.QUEUE_FULL;
        }
        return Result.SUCCESS;
    }
}
