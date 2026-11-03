package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.listener.RenewalFailureReason;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import com.mmrtr.lol.support.riot.RiotRetryScope;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumSet;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 갱신 ② 계정 단계. 게임 이름·태그를 저장하고 계정 비트를 켠다. */
@Slf4j
@Component
public class RenewalAccountStep {

    private final RenewalRecordPort recordPort;
    private final SummonerRepositoryPort summonerRepositoryPort;
    private final RiotApiService riotApiService;
    private final RenewalStepPublisher stepPublisher;
    private final RenewalFinisher finisher;
    private final Executor requestExecutor;
    private final RenewalStepFailures failures;

    public RenewalAccountStep(RenewalRecordPort recordPort, SummonerRepositoryPort summonerRepositoryPort,
                              RiotApiService riotApiService, RenewalStepPublisher stepPublisher,
                              RenewalFinisher finisher, MeterRegistry meterRegistry, Executor requestExecutor) {
        this.recordPort = recordPort;
        this.summonerRepositoryPort = summonerRepositoryPort;
        this.riotApiService = riotApiService;
        this.stepPublisher = stepPublisher;
        this.finisher = finisher;
        this.requestExecutor = requestExecutor;
        this.failures = new RenewalStepFailures("account", recordPort, finisher, meterRegistry);
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

        AccountDto account;
        try {
            account = RiotCallScope.call(RiotCallPriority.USER, () ->
                    RiotRetryScope.callWithoutRateLimitRetry(() ->
                            riotApiService.getAccountByPuuid(puuid, platform, requestExecutor).join()));
        } catch (Exception e) {
            failures.fetchFailed(puuid, renewalId, message.attempt(), e,
                    () -> stepPublisher.publish(RabbitMqBinding.RENEWAL_ACCOUNT, message.nextAttempt()),
                    () -> stepPublisher.requeueLater(RabbitMqBinding.RENEWAL_ACCOUNT, message));
            return;
        }

        try {
            summonerRepositoryPort.updateGameIdentity(puuid, account.getGameName(), account.getTagLine());
            finisher.finish(puuid, recordPort.markSteps(puuid, renewalId, EnumSet.of(RenewalStep.ACCOUNT)));
            failures.success();
        } catch (Exception e) {
            log.error("계정 단계 처리 실패. puuid={}", puuid, e);
            failures.fail(puuid, renewalId, e);
        }
    }
}
