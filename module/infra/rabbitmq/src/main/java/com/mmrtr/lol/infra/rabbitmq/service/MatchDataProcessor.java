package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.infra.redis.service.MatchRedisService;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
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
import java.util.Optional;
import java.util.Set;
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

    /** 나중 타임라인은 타임라인 허가 1건만 받는다. */
    private static final Map<RiotMethod, Integer> TIMELINE_COST = Map.of(RiotMethod.MATCH_TIMELINE, 1);

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
        QUEUE_FULL,
        /** Riot 호출 허가가 거절됐다. 처리 표시를 풀었고, 리스너가 잠시 쉰 뒤 다시 큐에 넣는다. */
        DENIED
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
        if (rank == MatchQueueRank.TIMELINE_LATER) {
            return processTimelineOnly(matchId, platformName, rank);
        }
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

        // 순위의 우선순위로 match·timeline 허가를 함께 받는다. 거절이면 표시를 풀고 DENIED 로 돌려준다
        RiotCallPriority priority = rank.riotPriority();
        Optional<RiotPermits> granted;
        try {
            granted = permitPort.tryAcquire(platformName, priority, MatchApiPort.MATCH_COST);
        } catch (RuntimeException e) {
            log.warn("Riot permit check failed for matchId {}: {}", matchId, e.toString());
            matchRedisService.releaseProcessing(matchId);
            return Result.FAILURE;
        }
        if (granted.isEmpty()) {
            log.debug("Riot permit denied for matchId {}", matchId);
            matchRedisService.releaseProcessing(matchId);
            return Result.DENIED;
        }
        RiotPermits permits = granted.get();
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

        if (!matchBatchProcessor.add(dtoPair.getFirst(), dtoPair.getSecond())) {
            log.warn("Match batch queue is full, requeueing matchId {}", matchId);
            matchRedisService.releaseProcessing(matchId);
            return Result.QUEUE_FULL;
        }
        return Result.SUCCESS;
    }

    /**
     * 나중 타임라인: 매치는 이미 DB 에 있으니 타임라인만 받아 저장한다.
     * 처리 표시는 쓰지 않는다 — 저장이 ON CONFLICT DO NOTHING 이라 같은 타임라인이 겹쳐도 안전하다.
     */
    private Result processTimelineOnly(String matchId, String platformName, MatchQueueRank rank) {
        try {
            Set<String> saved = matchRepositoryPort.findMatchIdsWithTimeline(List.of(matchId));
            if (saved != null && saved.contains(matchId)) {
                log.debug("Skipping timeline already in DB: {}", matchId);
                return Result.DUPLICATE;
            }
        } catch (RuntimeException e) {
            log.warn("DB timeline check failed for matchId {}: {}", matchId, e.toString());
            return Result.FAILURE;
        }

        Optional<RiotPermits> granted;
        try {
            granted = permitPort.tryAcquire(platformName, rank.riotPriority(), TIMELINE_COST);
        } catch (RuntimeException e) {
            log.warn("Riot permit check failed for timeline matchId {}: {}", matchId, e.toString());
            return Result.FAILURE;
        }
        if (granted.isEmpty()) {
            log.debug("Riot permit denied for timeline matchId {}", matchId);
            return Result.DENIED;
        }

        CompletableFuture<TimelineDto> timelineFuture =
                matchApiPort.fetchTimelineById(matchId, platformName, granted.get(), riotApiExecutor);
        TimelineDto timeline;
        try {
            timeline = timelineFuture.get(fetchTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout fetching timeline, requeueing matchId {}", matchId);
            timelineFuture.cancel(true);
            return Result.QUEUE_FULL;
        } catch (InterruptedException e) {
            log.warn("Interrupted while fetching timeline for matchId {}", matchId);
            Thread.currentThread().interrupt();
            return Result.FAILURE;
        } catch (ExecutionException e) {
            log.warn("Failed to fetch timeline for matchId {}: {}", matchId, e.getCause().getMessage());
            return Result.FAILURE;
        }

        if (!matchBatchProcessor.add(null, timeline)) {
            log.warn("Match batch queue is full, requeueing timeline matchId {}", matchId);
            return Result.QUEUE_FULL;
        }
        return Result.SUCCESS;
    }
}
