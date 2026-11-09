package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitDeniedException;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class MatchDataProcessor {

    /** match·timeline 을 받을 때까지 기다리는 상한. executor 가 밀린 경우까지 넉넉히 잡는다. */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(90);
    /** 매치 하나에 받는 허가. */
    private static final Map<RiotMethod, Integer> MATCH_COST = Map.of(RiotMethod.MATCH, 1, RiotMethod.MATCH_TIMELINE, 1);

    private final MatchApiPort matchApiPort;
    private final RiotPermitPort permitPort;
    private final MatchRedisService matchRedisService;
    private final MatchBatchProcessor matchBatchProcessor;
    private final Executor riotApiExecutor;
    private final MatchRepositoryPort matchRepositoryPort;
    private final MeterRegistry meterRegistry;
    private final Duration fetchTimeout;

    @Autowired
    public MatchDataProcessor(MatchApiPort matchApiPort, RiotPermitPort permitPort, MatchRedisService matchRedisService,
                              MatchBatchProcessor matchBatchProcessor, Executor riotApiExecutor,
                              MatchRepositoryPort matchRepositoryPort, MeterRegistry meterRegistry) {
        this(matchApiPort, permitPort, matchRedisService, matchBatchProcessor, riotApiExecutor,
                matchRepositoryPort, meterRegistry, FETCH_TIMEOUT);
    }

    MatchDataProcessor(MatchApiPort matchApiPort, RiotPermitPort permitPort, MatchRedisService matchRedisService,
                       MatchBatchProcessor matchBatchProcessor, Executor riotApiExecutor,
                       MatchRepositoryPort matchRepositoryPort, MeterRegistry meterRegistry,
                       Duration fetchTimeout) {
        this.matchApiPort = matchApiPort;
        this.permitPort = permitPort;
        this.matchRedisService = matchRedisService;
        this.matchBatchProcessor = matchBatchProcessor;
        this.riotApiExecutor = riotApiExecutor;
        this.matchRepositoryPort = matchRepositoryPort;
        this.meterRegistry = meterRegistry;
        this.fetchTimeout = fetchTimeout;
    }

    public enum Result {
        DUPLICATE,
        SUCCESS,
        FAILURE,
        QUEUE_FULL
    }

    public Result process(String matchId, String platformName, MatchQueueRank rank) {
        Result result = doProcess(matchId, platformName, rank);
        meterRegistry.counter("lol.match.process",
                "rank", rank.name().toLowerCase(Locale.ROOT),
                "result", result == Result.QUEUE_FULL ? "requeue" : result.name().toLowerCase(Locale.ROOT))
                .increment();
        return result;
    }

    private Result doProcess(String matchId, String platformName, MatchQueueRank rank) {
        List<String> existing;
        try {
            existing = matchRepositoryPort.findExistingMatchIds(List.of(matchId));
        } catch (RuntimeException e) {
            // 처리 표시를 잡기 전이라 풀 것이 없다. FAILURE 로 nack 해 DLX 에 남긴다 (MANUAL ack 라 던지면 un-acked 로 멈춘다)
            log.warn("DB check failed for matchId {}: {}", matchId, e.toString());
            return Result.FAILURE;
        }
        if (existing != null && existing.contains(matchId)) {
            log.debug("Skipping match already in DB: {}", matchId);
            return Result.DUPLICATE;
        }
        if (!matchRedisService.tryMarkProcessing(matchId)) {
            log.debug("Skipping duplicate matchId: {}", matchId);
            return Result.DUPLICATE;
        }

        // 순위의 우선순위로 match·timeline 허가를 함께 받는다. 거절은 예약 실패와 같게 다시 큐에 넣는다
        RiotCallPriority priority = rank.riotPriority();
        RiotPermits permits;
        try {
            permits = permitPort.tryAcquire(platformName, priority, MATCH_COST)
                    .orElseThrow(() -> new RiotPermitDeniedException(platformName, priority));
        } catch (RiotPermitDeniedException e) {
            log.warn("Riot permit denied, requeueing matchId {}", matchId);
            matchRedisService.releaseProcessing(matchId);
            return Result.QUEUE_FULL;
        } catch (RuntimeException e) {
            log.warn("Riot permit check failed for matchId {}: {}", matchId, e.toString());
            matchRedisService.releaseProcessing(matchId);
            return Result.FAILURE;
        }
        CompletableFuture<MatchDto> matchFuture =
                matchApiPort.fetchMatchById(matchId, platformName, permits, riotApiExecutor);
        CompletableFuture<TimelineDto> timelineFuture =
                matchApiPort.fetchTimelineById(matchId, platformName, permits, riotApiExecutor);

        Pair<MatchDto, TimelineDto> dtoPair;
        try {
            dtoPair = CompletableFuture.allOf(matchFuture, timelineFuture)
                    .thenApply(v -> Pair.of(matchFuture.join(), timelineFuture.join()))
                    .get(fetchTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // executor 가 밀려 늦어진 경우라 혼잡으로 보고 다시 큐에 넣는다
            log.warn("Timeout fetching match data, requeueing matchId {}", matchId);
            matchFuture.cancel(true);
            timelineFuture.cancel(true);
            matchRedisService.releaseProcessing(matchId);
            return Result.QUEUE_FULL;
        } catch (InterruptedException e) {
            log.warn("Interrupted while fetching match data for matchId {}", matchId);
            Thread.currentThread().interrupt();
            matchRedisService.releaseProcessing(matchId);
            return Result.FAILURE;
        } catch (ExecutionException e) {
            log.warn("Failed to fetch data for matchId {}: {}", matchId, e.getCause().getMessage());
            matchRedisService.releaseProcessing(matchId);
            return Result.FAILURE;
        }

        if (!matchBatchProcessor.add(dtoPair)) {
            log.warn("Match batch queue is full, requeueing matchId {}", matchId);
            matchRedisService.releaseProcessing(matchId);
            return Result.QUEUE_FULL;
        }
        return Result.SUCCESS;
    }
}
