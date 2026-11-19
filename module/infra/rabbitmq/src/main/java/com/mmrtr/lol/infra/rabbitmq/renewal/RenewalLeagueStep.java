package com.mmrtr.lol.infra.rabbitmq.renewal;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerApiPort;
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.RenewalStepMessage;
import com.mmrtr.lol.infra.rabbitmq.listener.RenewalFailureReason;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 갱신 ③ 리그 단계. 리그 항목을 저장하고 리그 비트를 켠다. */
@Slf4j
@Component
public class RenewalLeagueStep {

    private final RenewalRecordPort recordPort;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;
    private final SummonerApiPort summonerApiPort;
    private final RiotPermitPort permitPort;
    private final RenewalStepPublisher stepPublisher;
    private final RenewalFinisher finisher;
    private final Executor requestExecutor;
    private final RenewalStepFailures failures;

    public RenewalLeagueStep(RenewalRecordPort recordPort, SaveSummonerDataUseCase saveSummonerDataUseCase,
                             SummonerApiPort summonerApiPort, RiotPermitPort permitPort,
                             RenewalStepPublisher stepPublisher, RenewalFinisher finisher,
                             MeterRegistry meterRegistry, Executor requestExecutor) {
        this.recordPort = recordPort;
        this.saveSummonerDataUseCase = saveSummonerDataUseCase;
        this.summonerApiPort = summonerApiPort;
        this.permitPort = permitPort;
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

        Set<LeagueInfo> entries;
        try {
            Optional<RiotPermits> granted = permitPort
                    .tryAcquire(platform.name(), RiotCallPriority.USER, Map.of(RiotMethod.LEAGUE_ENTRIES_BY_PUUID, 1));
            if (granted.isEmpty()) {
                failures.denied(puuid, renewalId,
                        () -> stepPublisher.requeueLater(RabbitMqBinding.RENEWAL_LEAGUE, message));
                return;
            }
            RiotPermit permit = granted.get().take(RiotMethod.LEAGUE_ENTRIES_BY_PUUID);
            entries = summonerApiPort.fetchLeagueInfos(puuid, platform, permit, requestExecutor).join();
        } catch (Exception e) {
            failures.fetchFailed(puuid, renewalId, message.attempt(), e,
                    () -> stepPublisher.publish(RabbitMqBinding.RENEWAL_LEAGUE, message.nextAttempt()));
            return;
        }

        try {
            saveSummonerDataUseCase.saveLeagues(puuid, entries);
            finisher.finish(puuid, recordPort.markSteps(puuid, renewalId, EnumSet.of(RenewalStep.LEAGUE)));
            failures.success();
        } catch (Exception e) {
            log.error("리그 단계 처리 실패. puuid={}", puuid, e);
            failures.fail(puuid, renewalId, e);
        }
    }
}
