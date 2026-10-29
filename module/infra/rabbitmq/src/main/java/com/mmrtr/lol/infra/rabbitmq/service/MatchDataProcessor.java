package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import com.mmrtr.lol.infra.riot.exception.RiotBudgetExceededException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class MatchDataProcessor {

    /** BACKGROUND 한도 대기(backgroundMaxWait 30초)에 재시도 backoff 까지 들어가도록 넉넉히 잡는다. */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(90);

    private final MatchApiPort matchApiPort;
    private final MatchRedisService matchRedisService;
    private final MatchBatchProcessor matchBatchProcessor;
    private final Executor riotApiExecutor;
    private final Duration fetchTimeout;

    @Autowired
    public MatchDataProcessor(MatchApiPort matchApiPort, MatchRedisService matchRedisService,
                              MatchBatchProcessor matchBatchProcessor, Executor riotApiExecutor) {
        this(matchApiPort, matchRedisService, matchBatchProcessor, riotApiExecutor, FETCH_TIMEOUT);
    }

    MatchDataProcessor(MatchApiPort matchApiPort, MatchRedisService matchRedisService,
                       MatchBatchProcessor matchBatchProcessor, Executor riotApiExecutor, Duration fetchTimeout) {
        this.matchApiPort = matchApiPort;
        this.matchRedisService = matchRedisService;
        this.matchBatchProcessor = matchBatchProcessor;
        this.riotApiExecutor = riotApiExecutor;
        this.fetchTimeout = fetchTimeout;
    }

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
                    .get(fetchTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 한도 대기로 늦어진 경우가 대부분이라 혼잡으로 보고 다시 큐에 넣는다
            log.warn("Timeout fetching match data, requeueing matchId {}", matchId);
            matchFuture.cancel(true);
            timelineFuture.cancel(true);
            matchRedisService.releaseProcessing(matchId);
            return Result.QUEUE_FULL;
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
