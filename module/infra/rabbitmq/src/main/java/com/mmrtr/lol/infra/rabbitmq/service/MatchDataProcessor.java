package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final Executor riotApiExecutor;

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

        // 호출은 만드는 스레드의 스코프로 우선순위가 정해진다
        CompletableFuture<MatchDto> matchFuture = RiotCallScope.call(RiotCallPriority.BACKGROUND,
                () -> matchApiPort.fetchMatchById(matchId, platformName, riotApiExecutor));
        CompletableFuture<TimelineDto> timelineFuture = RiotCallScope.call(RiotCallPriority.BACKGROUND,
                () -> matchApiPort.fetchTimelineById(matchId, platformName, riotApiExecutor));

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
            if (isBudgetExceeded(e)) {
                log.warn("Riot budget exceeded, requeueing matchId {}", matchId);
                matchRedisService.releaseProcessing(matchId);
                return Result.QUEUE_FULL;
            }
            log.warn("Failed to fetch data for matchId {}: {}", matchId, e.getCause().getMessage());
            return Result.FAILURE;
        }

        if (!matchBatchProcessor.add(dtoPair)) {
            log.warn("Match batch queue is full, requeueing matchId {}", matchId);
            matchRedisService.releaseProcessing(matchId);
            return Result.QUEUE_FULL;
        }
        return Result.SUCCESS;
    }

    private boolean isBudgetExceeded(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RiotBudgetExceededException) {
                return true;
            }
        }
        return false;
    }
}
