package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerMessage;
import com.mmrtr.lol.infra.rabbitmq.listener.RenewalFailureReason;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRevisionChecker;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRevisionChecker.RevisionCheckResult;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import com.mmrtr.lol.support.riot.RiotRetryScope;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumSet;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 갱신 ① 소환사 단계. 아이콘·레벨을 저장하고 Riot 의 revisionDate 를 레코드에 남긴 뒤
 * 계정·리그·매치 단계 큐로 나눠 보낸다. revisionDate 가 같으면 네 단계를 한 번에 끝낸다.
 */
@Slf4j
@Component
public class RenewalSummonerStep {

    private final RenewalRecordPort recordPort;
    private final SummonerRepositoryPort summonerRepositoryPort;
    private final SummonerRevisionChecker revisionChecker;
    private final RiotApiService riotApiService;
    private final RenewalStepPublisher stepPublisher;
    private final RenewalFinisher finisher;
    private final Executor requestExecutor;
    private final RenewalStepFailures failures;

    public RenewalSummonerStep(RenewalRecordPort recordPort, SummonerRepositoryPort summonerRepositoryPort,
                               SummonerRevisionChecker revisionChecker, RiotApiService riotApiService,
                               RenewalStepPublisher stepPublisher, RenewalFinisher finisher,
                               MeterRegistry meterRegistry, Executor requestExecutor) {
        this.recordPort = recordPort;
        this.summonerRepositoryPort = summonerRepositoryPort;
        this.revisionChecker = revisionChecker;
        this.riotApiService = riotApiService;
        this.stepPublisher = stepPublisher;
        this.finisher = finisher;
        this.requestExecutor = requestExecutor;
        this.failures = new RenewalStepFailures("summoner", recordPort, finisher, meterRegistry);
    }

    public void run(SummonerMessage message) {
        String puuid = message.getPuuid();
        String renewalId = message.getRenewalId();

        if (!failures.isCurrent(puuid, renewalId)) {
            return;
        }

        Platform platform = Platform.valueOfName(message.getPlatformId());
        if (platform == null) {
            log.error("유효하지 않은 platform: {}, message: {}", message.getPlatformId(), message);
            failures.fail(puuid, renewalId, RenewalFailureReason.OTHER);
            return;
        }

        SummonerDto summonerDto;
        try {
            summonerDto = fetchSummoner(puuid, platform);
        } catch (Exception e) {
            handleFetchFailure(message, e);
            return;
        }

        try {
            saveAndDispatch(message, platform, summonerDto);
        } catch (Exception e) {
            log.error("소환사 단계 처리 실패. puuid={}", puuid, e);
            failures.fail(puuid, renewalId, e);
        }
    }

    private void saveAndDispatch(SummonerMessage message, Platform platform, SummonerDto summonerDto) {
        String puuid = message.getPuuid();
        String renewalId = message.getRenewalId();
        RevisionCheckResult check = revisionChecker.check(puuid, summonerDto);
        if (!check.needsRenewal()) {
            finisher.finish(puuid, recordPort.markSteps(puuid, renewalId, EnumSet.allOf(RenewalStep.class)));
            failures.success();
            return;
        }

        if (!summonerRepositoryPort.updateStatusInfo(
                puuid, summonerDto.getProfileIconId(), summonerDto.getSummonerLevel())) {
            failures.fail(puuid, renewalId, "not_found");
            return;
        }
        recordPort.saveRiotRevisionDate(puuid, renewalId, summonerDto.getRevisionDate());
        RenewalTransition transition = recordPort.markSteps(puuid, renewalId, EnumSet.of(RenewalStep.SUMMONER));

        String platformName = platform.name();
        stepPublisher.publish(RabbitMqBinding.RENEWAL_ACCOUNT,
                new RenewalStepMessage(renewalId, puuid, platformName, 0, 0));
        stepPublisher.publish(RabbitMqBinding.RENEWAL_LEAGUE,
                new RenewalStepMessage(renewalId, puuid, platformName, 0, 0));
        stepPublisher.publish(RabbitMqBinding.RENEWAL_MATCH,
                new RenewalStepMessage(renewalId, puuid, platformName, check.dbRevisionDateSeconds(), 0));
        finisher.finish(puuid, transition);
        failures.success();
    }

    private SummonerDto fetchSummoner(String puuid, Platform platform) {
        return RiotCallScope.call(RiotCallPriority.USER, () ->
                RiotRetryScope.callWithoutRateLimitRetry(() ->
                        riotApiService.getSummonerByPuuid(puuid, platform, requestExecutor).join()));
    }

    private void handleFetchFailure(SummonerMessage message, Exception e) {
        failures.fetchFailed(message.getPuuid(), message.getRenewalId(), message.getAttempt(), e,
                () -> stepPublisher.republishSummoner(message.nextAttempt()),
                () -> stepPublisher.republishSummonerLater(message));
    }
}
