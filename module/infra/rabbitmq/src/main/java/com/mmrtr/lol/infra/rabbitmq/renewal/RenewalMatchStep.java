package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchCacheWritePort;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.listener.RenewalFailureReason;
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import com.mmrtr.lol.infra.redis.service.RenewalMatchClaimService;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 갱신 ④ 매치 단계. 최근 매치 id 20개 중 DB 에 없는 것을 대상으로 기록하고, 선점한 대상의 match ∥ timeline 을
 * 한꺼번에 병렬로 받아 한 번에 저장한다. Riot 호출은 id 목록 1건, 매치마다 match·timeline 1건씩 USER 허가를 받는다.
 * 4xx(429 제외)로 실패한 매치만 실패 표시를 남기고(스펙 5.4), 그 밖의 실패
 * (429·허가 거절·5xx·시간 초과·IO 등)는 매치 적재 큐에 사용자 재시도 순위로 넘긴다. 실패 표시는 매치 단위라 그 매치를
 * 대상으로 둔 다른 갱신까지 match_failed 로 끝내므로, 매치 자체가 잘못된 경우에만 남긴다.
 * 계속 실패해 대상에서 뺀 매치는 버리지 않고 매치 적재 큐에 백필 순위로 넘긴다. 커서가 그 매치를 지나가도 잃지 않는다 (G8).
 * 대상이 전부 DB 에 들어가면 매치 비트를 켜고, 아니면 판정기가 DB 를 보고 켠다.
 * 밖으로 던지는 예외는 첫 전달의 저장 실패뿐이다 (재전달받아 DB 에 쓴 뒤에만 ack 한다).
 */
@Slf4j
@Component
public class RenewalMatchStep {

    private static final int MATCH_COUNT = 20;
    private static final String EXCLUDED_METRIC = "lol.renewal.match_excluded";

    private final RenewalRecordPort recordPort;
    private final MatchApiPort matchApiPort;
    private final RiotPermitPort permitPort;
    private final MatchRepositoryPort matchRepositoryPort;
    private final SaveMatchDataUseCase saveMatchDataUseCase;
    private final MatchCacheWritePort matchCacheWritePort;
    private final RenewalMatchClaimService claimService;
    private final MessageSender messageSender;
    private final RenewalStepPublisher stepPublisher;
    private final RenewalFinisher finisher;
    private final MeterRegistry meterRegistry;
    private final Executor requestExecutor;
    private final RenewalStepFailures failures;

    public RenewalMatchStep(RenewalRecordPort recordPort, MatchApiPort matchApiPort, RiotPermitPort permitPort,
                            MatchRepositoryPort matchRepositoryPort, SaveMatchDataUseCase saveMatchDataUseCase,
                            MatchCacheWritePort matchCacheWritePort, RenewalMatchClaimService claimService,
                            MessageSender messageSender, RenewalStepPublisher stepPublisher,
                            RenewalFinisher finisher, MeterRegistry meterRegistry, Executor requestExecutor) {
        this.recordPort = recordPort;
        this.matchApiPort = matchApiPort;
        this.permitPort = permitPort;
        this.matchRepositoryPort = matchRepositoryPort;
        this.saveMatchDataUseCase = saveMatchDataUseCase;
        this.matchCacheWritePort = matchCacheWritePort;
        this.claimService = claimService;
        this.messageSender = messageSender;
        this.stepPublisher = stepPublisher;
        this.finisher = finisher;
        this.meterRegistry = meterRegistry;
        this.requestExecutor = requestExecutor;
        this.failures = new RenewalStepFailures("match", recordPort, finisher, meterRegistry);
    }

    public void run(RenewalStepMessage message, boolean redelivered) {
        String puuid = message.puuid();
        String renewalId = message.renewalId();

        if (!failures.isCurrent(puuid, renewalId)) {
            return;
        }

        Platform platform = Platform.valueOfName(message.platform());
        if (platform == null) {
            log.error("유효하지 않은 platform: {}, message: {}", message.platform(), message);
            failures.fail(puuid, renewalId, RenewalFailureReason.OTHER);
            return;
        }
        String platformName = platform.name();

        List<String> ids;
        try {
            Optional<List<String>> fetched = fetchMatchIds(puuid, platformName, message.startTime());
            if (fetched.isEmpty()) {
                failures.denied(puuid, renewalId,
                        () -> stepPublisher.requeueLater(RabbitMqBinding.RENEWAL_MATCH, message));
                return;
            }
            ids = fetched.get();
        } catch (Exception e) {
            failures.fetchFailed(puuid, renewalId, message.attempt(), e,
                    () -> stepPublisher.publish(RabbitMqBinding.RENEWAL_MATCH, message.nextAttempt()));
            return;
        }

        List<String> claimed = List.of();
        try {
            Set<String> targets = recordTargets(puuid, renewalId, platformName, ids);
            claimed = claim(targets, renewalId);
            completeClaimed(message, platformName, targets, claimed);
            handOffRest(message, platformName, ids);
            failures.success();
        } catch (MatchSaveFailure e) {
            onSaveFailure(message, redelivered, claimed, e.failure);
        } catch (Exception e) {
            log.error("매치 단계 처리 실패. puuid={}", puuid, e);
            releaseQuietly(claimed, renewalId);
            failures.fail(puuid, renewalId, e);
        }
    }

    /** USER 허가 1건을 받아 부른다. 허가가 거절되면 비어 있다. */
    private Optional<List<String>> fetchMatchIds(String puuid, String platformName, long startTime) {
        Optional<RiotPermits> permits = permitPort
                .tryAcquire(platformName, RiotCallPriority.USER, Map.of(RiotMethod.MATCH_IDS_BY_PUUID, 1));
        if (permits.isEmpty()) {
            return Optional.empty();
        }
        List<String> ids = matchApiPort.fetchMatchIdsByPuuid(
                puuid, platformName, startTime, 0, MATCH_COUNT, permits.get(), requestExecutor).join();
        return Optional.of(ids == null ? List.of() : ids);
    }

    /**
     * DB 에 없고 계속 실패해 빠지지 않은 매치를 대상으로 기록한다. 0개여도 기록해 targetsReady 를 켠다.
     * 뺀 매치는 로그와 지표({@code lol.renewal.match_excluded})로 남기고(스펙 5.6), 매치 적재 큐에 백필 순위로 넘긴다.
     * 넘기기가 실패하면 예외가 run 까지 올라가 갱신이 실패하므로 커서가 그 매치를 지나가지 않는다.
     */
    private Set<String> recordTargets(String puuid, String renewalId, String platformName, List<String> ids) {
        Set<String> targets = new LinkedHashSet<>(ids);
        if (!targets.isEmpty()) {
            targets.removeAll(matchRepositoryPort.findExistingMatchIds(ids));
        }
        Set<String> excluded = targets.isEmpty() ? Set.of() : claimService.findExcluded(targets);
        if (!excluded.isEmpty()) {
            log.warn("계속 실패한 매치를 갱신 대상에서 빼고 매치 적재 큐에 백필로 넘긴다. puuid={}, renewalId={}, matchIds={}",
                    puuid, renewalId, excluded);
            excluded.forEach(matchId -> messageSender.sendMessageByMatchId(matchId, platformName, MatchQueueRank.BACKFILL));
        }
        targets.removeAll(excluded);
        meterRegistry.counter(EXCLUDED_METRIC).increment(excluded.size());
        recordPort.recordTargets(puuid, renewalId, targets);
        return targets;
    }

    /** 다른 갱신이 쥔 매치는 부르지 않는다. 그 갱신이 저장하면 판정기가 두 갱신을 함께 끝낸다. */
    private List<String> claim(Set<String> targets, String renewalId) {
        return targets.stream().filter(matchId -> claimService.claim(matchId, renewalId)).toList();
    }

    /** 선점한 매치를 받아 저장하고 못 받은 매치를 넘긴다. 대상이 전부 이번에 저장됐으면 매치 비트를 켠다. */
    private void completeClaimed(RenewalStepMessage message, String platformName,
                                 Set<String> targets, List<String> claimed) {
        List<MatchFetch> results = fetchAll(claimed, platformName);
        List<MatchFetch> fetched = results.stream().filter(r -> r.outcome() == Outcome.FETCHED).toList();
        save(message.puuid(), message.renewalId(), fetched);
        handOffUnfetched(results, platformName, message.renewalId());
        if (fetched.size() == targets.size()) {
            finisher.finish(message.puuid(),
                    recordPort.markSteps(message.puuid(), message.renewalId(), EnumSet.of(RenewalStep.MATCH)));
        }
    }

    /** 매치마다 match ∥ timeline 을 한꺼번에 시작한 뒤 모두 기다린다. */
    private List<MatchFetch> fetchAll(List<String> matchIds, String platformName) {
        List<PendingFetch> pending = matchIds.stream().map(matchId -> start(matchId, platformName)).toList();
        return pending.stream().map(RenewalMatchStep::await).toList();
    }

    /**
     * 매치마다 따로 USER 허가(match·timeline 1건씩)를 받아 둘을 시작한다. 거절되면 부르지 않고 {@link PendingFetch#denied}
     * 로 두어 {@link #await} 가 HAND_OFF 로 넘기게 한다. 허가를 묻지 못해도 그 예외로 실패한 것으로 두어 같다.
     */
    private PendingFetch start(String matchId, String platformName) {
        Optional<RiotPermits> permits;
        try {
            permits = permitPort.tryAcquire(platformName, RiotCallPriority.USER, MatchApiPort.MATCH_COST);
        } catch (RuntimeException e) {
            return new PendingFetch(matchId, CompletableFuture.failedFuture(e), CompletableFuture.failedFuture(e));
        }
        if (permits.isEmpty()) {
            return PendingFetch.denied(matchId);
        }
        return new PendingFetch(matchId,
                matchApiPort.fetchMatchById(matchId, platformName, permits.get(), requestExecutor),
                matchApiPort.fetchTimelineById(matchId, platformName, permits.get(), requestExecutor));
    }

    /**
     * 둘 다 받으면 FETCHED. 하나라도 4xx(429 제외)면 CLIENT_ERROR — 다시 불러도 같으니 넘기지 않는다.
     * 그 밖의 실패(429·허가 거절·5xx·시간 초과·IO·역직렬화 등)는 HAND_OFF.
     */
    private static MatchFetch await(PendingFetch pending) {
        if (pending.match() == null) {
            log.warn("갱신 매치 허가 거절. matchId={}, outcome={}", pending.matchId(), Outcome.HAND_OFF);
            return new MatchFetch(pending.matchId(), Outcome.HAND_OFF, null, null);
        }
        Throwable matchFailure = failureOf(pending.match());
        Throwable timelineFailure = failureOf(pending.timeline());
        if (matchFailure == null && timelineFailure == null) {
            return new MatchFetch(pending.matchId(), Outcome.FETCHED,
                    pending.match().join(), pending.timeline().join());
        }
        Outcome outcome = isClientError(matchFailure) || isClientError(timelineFailure)
                ? Outcome.CLIENT_ERROR : Outcome.HAND_OFF;
        log.warn("갱신 매치 조회 실패. matchId={}, outcome={}, match={}, timeline={}",
                pending.matchId(), outcome, describe(matchFailure), describe(timelineFailure));
        return new MatchFetch(pending.matchId(), outcome, null, null);
    }

    /** 받은 매치를 한 번에 저장하고 요청자 캐시를 쓴 뒤 선점을 푼다. 저장 예외는 {@link MatchSaveFailure} 로 올린다. */
    private void save(String puuid, String renewalId, List<MatchFetch> fetched) {
        if (fetched.isEmpty()) {
            return;
        }
        List<MatchDto> matches = fetched.stream().map(MatchFetch::match).toList();
        List<TimelineDto> timelines = fetched.stream().map(MatchFetch::timeline).toList();
        try {
            saveMatchDataUseCase.execute(matches, timelines);
        } catch (RuntimeException e) {
            throw new MatchSaveFailure(e);
        }
        matchCacheWritePort.writeMatches(matches, timelines, puuid);
        fetched.forEach(result -> claimService.release(result.matchId(), renewalId));
    }

    /** 4xx 만 실패 표시를 남기고, 그 밖의 실패는 매치 적재 큐에 사용자 재시도 순위로 넘긴다. 어느 쪽이든 선점을 푼다. */
    private void handOffUnfetched(List<MatchFetch> results, String platformName, String renewalId) {
        for (MatchFetch result : results) {
            if (result.outcome() == Outcome.FETCHED) {
                continue;
            }
            if (result.outcome() == Outcome.CLIENT_ERROR) {
                claimService.markFailed(result.matchId());
            } else {
                messageSender.sendMessageByMatchId(result.matchId(), platformName, MatchQueueRank.USER_RETRY);
            }
            claimService.release(result.matchId(), renewalId);
        }
    }

    /** 20개를 꽉 채웠으면 21번째부터는 백그라운드 매치 검색이 찾는다. */
    private void handOffRest(RenewalStepMessage message, String platformName, List<String> ids) {
        if (ids.size() == MATCH_COUNT) {
            stepPublisher.publishMatchFind(
                    new SummonerRenewalMessage(message.puuid(), platformName, message.startTime(), MATCH_COUNT));
        }
    }

    /**
     * 첫 전달이면 저장 예외를 다시 던져 메시지를 다시 받는다. 선점은 내 renewalId 라 재전달 때 다시 잡힌다.
     * 이미 재전달된 메시지면 같은 메시지가 끝없이 돌지 않게 갱신을 실패시키고 선점을 푼 뒤 정상 반환한다.
     */
    private void onSaveFailure(RenewalStepMessage message, boolean redelivered, List<String> claimed,
                               RuntimeException failure) {
        String puuid = message.puuid();
        String renewalId = message.renewalId();
        if (!redelivered) {
            log.warn("매치 저장 실패. 메시지를 다시 받는다. puuid={}, renewalId={}, cause={}", puuid, renewalId, failure.toString());
            throw failure;
        }
        log.error("재전달된 메시지의 매치 저장도 실패. puuid={}, renewalId={}", puuid, renewalId, failure);
        releaseQuietly(claimed, renewalId);
        failures.fail(puuid, renewalId, failure);
    }

    private void releaseQuietly(List<String> matchIds, String renewalId) {
        for (String matchId : matchIds) {
            try {
                claimService.release(matchId, renewalId);
            } catch (Exception e) {
                log.warn("매치 선점 해제 실패. TTL 로 풀린다. matchId={}, renewalId={}, cause={}",
                        matchId, renewalId, e.toString());
            }
        }
    }

    /** cause 사슬의 Riot 예외가 429 가 아닌 4xx({@link RiotClientException}, 404 포함)면 true. 허가 거절은 Riot 예외가 아니다. */
    private static boolean isClientError(Throwable failure) {
        RiotClientException client = RenewalStepFailures.findCause(failure, RiotClientException.class);
        return client != null
                && !(client instanceof RiotRateLimitException)
                && (client.getStatus() == null || client.getStatus().value() != 429);
    }

    private static Throwable failureOf(CompletableFuture<?> future) {
        try {
            future.join();
            return null;
        } catch (CompletionException | CancellationException e) {
            return e;
        }
    }

    private static String describe(Throwable failure) {
        if (failure == null) {
            return "ok";
        }
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        return cause.toString();
    }

    /** FETCHED: 저장한다. CLIENT_ERROR: 4xx 라 실패 표시. HAND_OFF: 매치 적재 큐에 사용자 재시도 순위로. */
    private enum Outcome { FETCHED, CLIENT_ERROR, HAND_OFF }

    /** 허가가 거절돼 부르지 않은 매치는 match·timeline 이 null 이다. */
    private record PendingFetch(String matchId, CompletableFuture<MatchDto> match,
                                CompletableFuture<TimelineDto> timeline) {

        static PendingFetch denied(String matchId) {
            return new PendingFetch(matchId, null, null);
        }
    }

    /** FETCHED 일 때만 match·timeline 이 있다. */
    private record MatchFetch(String matchId, Outcome outcome, MatchDto match, TimelineDto timeline) {
    }

    /** 저장 예외를 run 까지 올려 그 밖의 예외와 가른다. */
    private static final class MatchSaveFailure extends RuntimeException {

        private final RuntimeException failure;

        MatchSaveFailure(RuntimeException failure) {
            super(failure);
            this.failure = failure;
        }
    }
}
