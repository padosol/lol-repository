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
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchFetcher.Fetched;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchFetcher.Result;
import com.mmrtr.lol.infra.rabbitmq.renewal.RenewalMatchPlan.Item;
import com.mmrtr.lol.infra.rabbitmq.service.MatchQueueRank;
import com.mmrtr.lol.infra.rabbitmq.service.MessageSender;
import com.mmrtr.lol.infra.redis.service.RenewalMatchClaimService;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 갱신 ④ 매치 단계. 최근 매치 id 20개(최신순)로 {@link RenewalMatchPlan} 을 만든다. 매치 대상은 DB 에 없는 매치,
 * 타임라인 대상은 앞 3개 중 타임라인이 DB 에 없는 것이다. 매치 대상을 레코드에 기록하고 선점한 뒤
 * {@link RenewalMatchFetcher} 로 묶음 허가를 받아 한꺼번에 받고, 받은 것을 한 번에 저장한다.
 * 못 받은 것은 넘긴다: 허가가 끝내 거절됐거나 match 가 429·5xx·IO·시간 초과로 실패한 매치는 매치 적재 큐에
 * 사용자 재시도 순위로, 타임라인을 받지 못한 것(저장한 매치·타임라인만 필요한 것)은 나중 타임라인 순위로 넘긴다.
 * match 가 4xx(429 제외)인 매치만 실패 표시를 남긴다(스펙 5.4). 실패 표시는 매치 단위라 그 매치를 대상으로 둔
 * 다른 갱신까지 match_failed 로 끝내므로, 매치 자체가 잘못된 경우에만 남긴다.
 * 계속 실패해 대상에서 뺀 매치는 버리지 않고 매치 적재 큐에 백필 순위로 넘긴다. 커서가 그 매치를 지나가도 잃지 않는다 (G8).
 * 매치 대상이 전부 저장되면 매치 비트를 켜고(최신 3게임 타임라인은 조건이 아니다), 아니면 판정기가 DB 를 보고 켠다.
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
    private final RenewalMatchFetcher fetcher;
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
                            RenewalMatchFetcher fetcher, MatchRepositoryPort matchRepositoryPort,
                            SaveMatchDataUseCase saveMatchDataUseCase, MatchCacheWritePort matchCacheWritePort,
                            RenewalMatchClaimService claimService, MessageSender messageSender,
                            RenewalStepPublisher stepPublisher, RenewalFinisher finisher,
                            MeterRegistry meterRegistry, Executor requestExecutor) {
        this.recordPort = recordPort;
        this.matchApiPort = matchApiPort;
        this.permitPort = permitPort;
        this.fetcher = fetcher;
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

        List<String> claimed = new ArrayList<>();
        try {
            RenewalMatchPlan plan = plan(puuid, renewalId, platformName, ids);
            // 대상이 0개여도 기록한다 (판정기가 대상이 준비됐는지 본다).
            recordPort.recordTargets(puuid, renewalId, plan.matchTargets());
            List<Item> items = claim(plan.items(), renewalId, claimed);
            Result result = fetcher.fetch(platformName, items);
            int savedMatches = save(puuid, result.fetched());
            handOff(platformName, result);
            releaseQuietly(claimed, renewalId);
            if (savedMatches == plan.matchTargets().size()) {
                finisher.finish(puuid, recordPort.markSteps(puuid, renewalId, EnumSet.of(RenewalStep.MATCH)));
            }
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
     * 매치 대상과 타임라인 대상을 정한다. 계속 실패해 빠진 매치는 대상에서 빼고 로그와 지표({@code lol.renewal.match_excluded})로
     * 남기며(스펙 5.6), 매치 적재 큐에 백필 순위로 넘긴다. 넘기기가 실패하면 예외가 run 까지 올라가 갱신이 실패하므로
     * 커서가 그 매치를 지나가지 않는다.
     */
    private RenewalMatchPlan plan(String puuid, String renewalId, String platformName, List<String> ids) {
        Set<String> existing = ids.isEmpty() ? Set.of() : new HashSet<>(matchRepositoryPort.findExistingMatchIds(ids));
        Set<String> candidates = new LinkedHashSet<>(ids);
        candidates.removeAll(existing);
        Set<String> excluded = candidates.isEmpty() ? Set.of() : claimService.findExcluded(candidates);
        if (!excluded.isEmpty()) {
            log.warn("계속 실패한 매치를 갱신 대상에서 빼고 매치 적재 큐에 백필로 넘긴다. puuid={}, renewalId={}, matchIds={}",
                    puuid, renewalId, excluded);
            excluded.forEach(matchId -> messageSender.sendMessageByMatchId(matchId, platformName, MatchQueueRank.BACKFILL));
        }
        meterRegistry.counter(EXCLUDED_METRIC).increment(excluded.size());
        List<String> recent = ids.subList(0, Math.min(RenewalMatchPlan.TIMELINE_COUNT, ids.size()));
        Set<String> withTimeline = recent.isEmpty() ? Set.of() : matchRepositoryPort.findMatchIdsWithTimeline(recent);
        return RenewalMatchPlan.of(ids, existing, excluded, withTimeline);
    }

    /**
     * 매치 대상만 선점하고, 받을 항목을 돌려준다. 다른 갱신이 쥔 매치는 부르지 않는다 — 그 갱신이 저장하면 판정기가
     * 두 갱신을 함께 끝낸다. 단 타임라인 대상이면 타임라인만 받는다. 선점한 id 는 도중에 던져도 풀 수 있게 claimed 에 모은다.
     */
    private List<Item> claim(List<Item> items, String renewalId, List<String> claimed) {
        List<Item> toFetch = new ArrayList<>();
        for (Item item : items) {
            if (!item.match()) {
                toFetch.add(item);
            } else if (claimService.claim(item.matchId(), renewalId)) {
                claimed.add(item.matchId());
                toFetch.add(item);
            } else if (item.timeline()) {
                toFetch.add(item.timelineOnly());
            }
        }
        return toFetch;
    }

    /**
     * 받은 매치·타임라인을 한 번에 저장하고 요청자 캐시를 쓴다. 저장한 매치 수를 돌려준다.
     * 저장 예외는 {@link MatchSaveFailure} 로 올린다.
     */
    private int save(String puuid, List<Fetched> fetched) {
        if (fetched.isEmpty()) {
            return 0;
        }
        List<MatchDto> matches = fetched.stream().map(Fetched::match).filter(Objects::nonNull).toList();
        List<TimelineDto> timelines = fetched.stream().map(Fetched::timeline).filter(Objects::nonNull).toList();
        try {
            saveMatchDataUseCase.execute(matches, timelines);
        } catch (RuntimeException e) {
            throw new MatchSaveFailure(e);
        }
        matchCacheWritePort.writeMatches(matches, timelines, puuid);
        return matches.size();
    }

    /**
     * 못 받은 것을 매치 적재 큐에 id 로 넘긴다. 허가가 거절된 매치와 match 가 429·5xx·IO·시간 초과로 실패한 매치는
     * 사용자 재시도 순위로(그 길은 match·timeline 을 둘 다 받는다), 타임라인을 받지 못한 것 — 거절된 타임라인만 항목,
     * 타임라인 없이 저장한 매치, 실패한 타임라인 — 은 id 마다 한 번 나중 타임라인 순위로 넘긴다. 타임라인만 필요한 항목을
     * 사용자 재시도로 넘기면 DB 에 있는 매치라 DUPLICATE 로 버려지기 때문이다. match 가 4xx 면 실패 표시만 남긴다.
     */
    private void handOff(String platformName, Result result) {
        Set<String> timelineLater = new LinkedHashSet<>();
        for (Item item : result.denied()) {
            if (item.match()) {
                messageSender.sendMessageByMatchId(item.matchId(), platformName, MatchQueueRank.USER_RETRY);
            } else {
                timelineLater.add(item.matchId());
            }
        }
        result.fetched().stream()
                .filter(fetched -> fetched.match() != null && fetched.timeline() == null)
                .forEach(fetched -> timelineLater.add(fetched.matchId()));
        timelineLater.addAll(result.timelineLater());
        timelineLater.forEach(matchId ->
                messageSender.sendMessageByMatchId(matchId, platformName, MatchQueueRank.TIMELINE_LATER));
        result.userRetry().forEach(matchId ->
                messageSender.sendMessageByMatchId(matchId, platformName, MatchQueueRank.USER_RETRY));
        result.clientError().forEach(claimService::markFailed);
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

    /** 저장 예외를 run 까지 올려 그 밖의 예외와 가른다. */
    private static final class MatchSaveFailure extends RuntimeException {

        private final RuntimeException failure;

        MatchSaveFailure(RuntimeException failure) {
            super(failure);
            this.failure = failure;
        }
    }
}
