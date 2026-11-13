package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.match.application.port.MatchApiPort;
import com.mmrtr.lol.domain.match.application.port.MatchCacheWritePort;
import com.mmrtr.lol.domain.match.application.port.MatchRepositoryPort;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 갱신 ④ 매치 단계 (스펙 2026-11-13). 최근 매치 id 20개(최신순)를 받아 차례로 한다.
 * <ol>
 *   <li>20개면 21번째부터를 추가 매치 찾기 큐에 넘긴다 (추가 갱신이 켜졌을 때만).</li>
 *   <li>DB 에 있는 매치와 다른 갱신이 선점한 매치를 뺀다. 그 매치를 기다리지 않는다.</li>
 *   <li>남은 id 의 앞 3개는 매치와 타임라인, 나머지는 매치만 묶음 허가를 받고({@link RenewalMatchFetcher}),
 *       허가된 id 를 선점(1분, 풀지 않음)한 뒤 부른다. 1개까지 줄여도 거절된 id 는 매치 적재 큐에 사용자 재시도로 넘긴다.</li>
 *   <li>받은 매치를 요청자 캐시에 쓰고 매치 비트를 켠다. DB 는 {@link RenewalMatchSaver} 가 비동기로 저장한다.</li>
 *   <li>타임라인 없이 받은 매치는 나중 타임라인으로 넘긴다. 발행 실패는 갱신을 실패시키지 않는다.</li>
 * </ol>
 * 부른 뒤 실패한 매치는 누락으로 두고 다시 받지 않는다. 거절 넘기기나 캐시 쓰기가 실패하면 갱신을 실패시키되,
 * 받은 매치는 그대로 저장한다. 밖으로 예외를 던지지 않는다.
 */
@Slf4j
@Component
public class RenewalMatchStep {

    private static final int MATCH_COUNT = 20;
    private static final String MISSING_METRIC = "lol.renewal.match_missing";
    private static final String TIMELINE_LATER_PUBLISH_FAILED_METRIC = "lol.renewal.timeline_later.publish_failed";

    private final RenewalRecordPort recordPort;
    private final MatchApiPort matchApiPort;
    private final RiotPermitPort permitPort;
    private final RenewalMatchFetcher fetcher;
    private final MatchRepositoryPort matchRepositoryPort;
    private final RenewalMatchSaver saver;
    private final MatchCacheWritePort matchCacheWritePort;
    private final RenewalMatchClaimService claimService;
    private final MessageSender messageSender;
    private final RenewalStepPublisher stepPublisher;
    private final RenewalFinisher finisher;
    private final MeterRegistry meterRegistry;
    private final Executor requestExecutor;
    private final RenewalStepFailures failures;
    private final boolean backgroundEnabled;

    public RenewalMatchStep(RenewalRecordPort recordPort, MatchApiPort matchApiPort, RiotPermitPort permitPort,
                            RenewalMatchFetcher fetcher, MatchRepositoryPort matchRepositoryPort,
                            RenewalMatchSaver saver, MatchCacheWritePort matchCacheWritePort,
                            RenewalMatchClaimService claimService, MessageSender messageSender,
                            RenewalStepPublisher stepPublisher, RenewalFinisher finisher,
                            MeterRegistry meterRegistry, Executor requestExecutor,
                            @Value("${lol.renewal.background.enabled:true}") boolean backgroundEnabled) {
        this.recordPort = recordPort;
        this.matchApiPort = matchApiPort;
        this.permitPort = permitPort;
        this.fetcher = fetcher;
        this.matchRepositoryPort = matchRepositoryPort;
        this.saver = saver;
        this.matchCacheWritePort = matchCacheWritePort;
        this.claimService = claimService;
        this.messageSender = messageSender;
        this.stepPublisher = stepPublisher;
        this.finisher = finisher;
        this.meterRegistry = meterRegistry;
        this.requestExecutor = requestExecutor;
        this.failures = new RenewalStepFailures("match", recordPort, finisher, meterRegistry);
        this.backgroundEnabled = backgroundEnabled;
    }

    public void run(RenewalStepMessage message) {
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

        try {
            handOffRest(message, platformName, ids);
            List<String> remaining = remaining(ids, renewalId);
            Result result = fetcher.fetch(platformName, RenewalMatchPlan.items(remaining),
                    matchId -> claimService.claim(matchId, renewalId));
            List<MatchDto> matches = result.fetched().stream().map(Fetched::match).toList();
            List<TimelineDto> timelines = result.fetched().stream()
                    .map(Fetched::timeline).filter(Objects::nonNull).toList();
            try {
                handOffDenied(platformName, result.denied());
                matchCacheWritePort.writeMatches(matches, timelines, puuid);
                finisher.finish(puuid, recordPort.markSteps(puuid, renewalId, EnumSet.of(RenewalStep.MATCH)));
            } finally {
                // 실패 경로에서도 받은 것은 저장한다 (스펙 4장 9단계)
                saver.save(matches, timelines);
            }
            handOffTimelineLater(platformName, result.fetched());
            countMissing("call_failed", result.failed().size());
            countMissing("claimed_by_other", result.claimedElsewhere().size());
            failures.success();
        } catch (Exception e) {
            log.error("매치 단계 처리 실패. puuid={}", puuid, e);
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

    /** 20개를 꽉 채웠으면 21번째부터는 백그라운드 매치 검색이 찾는다. 추가 갱신이 꺼져 있으면 넘기지 않는다. */
    private void handOffRest(RenewalStepMessage message, String platformName, List<String> ids) {
        if (backgroundEnabled && ids.size() == MATCH_COUNT) {
            stepPublisher.publishMatchFind(
                    new SummonerRenewalMessage(message.puuid(), platformName, message.startTime(), MATCH_COUNT));
        }
    }

    /**
     * 받을 id. 중복을 빼고(Riot 순서 유지), DB 에 있는 매치와 다른 갱신이 선점한 매치를 뺀다.
     * 선점한 매치는 그 갱신이 받으므로 기다리지 않고, {@code claimed_by_other} 로 센다.
     */
    private List<String> remaining(List<String> ids, String renewalId) {
        Set<String> candidates = new LinkedHashSet<>(ids);
        if (candidates.isEmpty()) {
            return List.of();
        }
        candidates.removeAll(new HashSet<>(matchRepositoryPort.findExistingMatchIds(new ArrayList<>(candidates))));
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<String> claimedByOthers = claimService.findClaimedByOthers(candidates, renewalId);
        candidates.removeAll(claimedByOthers);
        countMissing("claimed_by_other", claimedByOthers.size());
        return List.copyOf(candidates);
    }

    /**
     * 1개까지 줄여도 허가가 거절된 매치를 매치 적재 큐에 사용자 재시도로 넘긴다. 백그라운드가 나중에 적재하므로
     * 갱신 성공 조건에서 뺀다. 발행이 던지면 run 까지 올려 갱신을 실패시킨다 (커서가 옮겨지지 않아 다음 갱신이 다시 받는다).
     */
    private void handOffDenied(String platformName, List<Item> denied) {
        for (Item item : denied) {
            messageSender.sendMessageByMatchId(item.matchId(), platformName, MatchQueueRank.USER_RETRY);
            countMissing("denied_queued", 1);
        }
    }

    /**
     * 타임라인 없이 받은 매치(앞 3개 밖이거나 타임라인만 실패한 매치)를 id 마다 나중 타임라인 순위로 넘긴다.
     * 타임라인은 갱신 성공 조건이 아니라서 발행 실패는 경고 로그와
     * {@code lol.renewal.timeline_later.publish_failed} 로 남기고 다음 id 로 간다.
     */
    private void handOffTimelineLater(String platformName, List<Fetched> fetched) {
        for (Fetched item : fetched) {
            if (item.timeline() != null) {
                continue;
            }
            try {
                messageSender.sendMessageByMatchId(item.matchId(), platformName, MatchQueueRank.TIMELINE_LATER);
            } catch (RuntimeException e) {
                log.warn("나중 타임라인 발행 실패. 그 타임라인은 채워지지 않는다. matchId={}, cause={}",
                        item.matchId(), e.toString());
                meterRegistry.counter(TIMELINE_LATER_PUBLISH_FAILED_METRIC).increment();
            }
        }
    }

    private void countMissing(String reason, int count) {
        if (count > 0) {
            meterRegistry.counter(MISSING_METRIC, "reason", reason).increment(count);
        }
    }
}
