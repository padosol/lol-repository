package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.listener.RenewalFailureReason;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.riot.RiotRetryScope;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 갱신 ③ 리그 단계. 리그 항목을 저장하고 리그 비트를 켠다. */
@Slf4j
@Component
public class RenewalLeagueStep {

    private final RenewalRecordPort recordPort;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;
    private final RiotApiService riotApiService;
    private final RenewalStepPublisher stepPublisher;
    private final RenewalFinisher finisher;
    private final Executor requestExecutor;
    private final RenewalStepFailures failures;

    public RenewalLeagueStep(RenewalRecordPort recordPort, SaveSummonerDataUseCase saveSummonerDataUseCase,
                             RiotApiService riotApiService, RenewalStepPublisher stepPublisher,
                             RenewalFinisher finisher, MeterRegistry meterRegistry, Executor requestExecutor) {
        this.recordPort = recordPort;
        this.saveSummonerDataUseCase = saveSummonerDataUseCase;
        this.riotApiService = riotApiService;
        this.stepPublisher = stepPublisher;
        this.finisher = finisher;
        this.requestExecutor = requestExecutor;
        this.failures = new RenewalStepFailures("league", recordPort, finisher, meterRegistry);
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

        Set<LeagueEntryDto> entries;
        try {
            entries = RiotRetryScope.callWithoutRateLimitRetry(() ->
                    riotApiService.getLeagueEntriesByPuuid(puuid, platform, requestExecutor).join());
        } catch (Exception e) {
            failures.fetchFailed(puuid, renewalId, message.attempt(), e,
                    () -> stepPublisher.publish(RabbitMqBinding.RENEWAL_LEAGUE, message.nextAttempt()),
                    () -> stepPublisher.requeueLater(RabbitMqBinding.RENEWAL_LEAGUE, message));
            return;
        }

        try {
            saveSummonerDataUseCase.saveLeagues(puuid, toLeagueInfos(entries));
            finisher.finish(puuid, recordPort.markSteps(puuid, renewalId, EnumSet.of(RenewalStep.LEAGUE)));
            failures.success();
        } catch (Exception e) {
            log.error("리그 단계 처리 실패. puuid={}", puuid, e);
            failures.fail(puuid, renewalId, e);
        }
    }

    private static Set<LeagueInfo> toLeagueInfos(Set<LeagueEntryDto> entries) {
        return entries.stream()
                .map(dto -> LeagueInfo.builder()
                        .leagueId(dto.getLeagueId())
                        .queueType(dto.getQueueType())
                        .tier(dto.getTier())
                        .rank(dto.getRank())
                        .leaguePoints(dto.getLeaguePoints())
                        .wins(dto.getWins())
                        .losses(dto.getLosses())
                        .hotStreak(dto.isHotStreak())
                        .veteran(dto.isVeteran())
                        .freshBlood(dto.isFreshBlood())
                        .inactive(dto.isInactive())
                        .build())
                .collect(Collectors.toSet());
    }
}
