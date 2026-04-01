package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.domain.match.application.usecase.SaveMatchDataUseCase;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MatchDataFetcher.FetchNewMatchIdsResult;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRevisionChecker.RevisionCheckResult;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerRenewalService {

    private final SummonerDataCollector summonerDataCollector;
    private final SummonerRevisionChecker summonerRevisionChecker;
    private final SummonerRepositoryPort summonerRepositoryPort;
    private final MatchDataFetcher matchDataFetcher;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;
    private final SaveMatchDataUseCase saveMatchDataUseCase;
    private final RabbitTemplate rabbitTemplate;
    private final Executor requestExecutor;

    public void renewSummoner(String puuid, Platform platform) {
        log.info("[갱신 시작] puuid={}", puuid);

        SummonerDto summonerDto = summonerDataCollector.fetchSummoner(puuid, platform, requestExecutor);
        if (summonerDto == null) {
            log.error("RIOT API에서 소환사 정보를 조회할 수 없습니다. puuid: {}", puuid);
            return;
        }

        RevisionCheckResult revisionCheck = summonerRevisionChecker.check(puuid, summonerDto);
        if (!revisionCheck.needsRenewal()) {
            summonerRepositoryPort.updateLastRiotCallDate(puuid);
            return;
        }

        Optional<Summoner> summonerOpt = summonerDataCollector
                .collectAndAssemble(puuid, platform, summonerDto, requestExecutor);
        if (summonerOpt.isEmpty()) {
            return;
        }

        Summoner summoner = summonerOpt.get();
        FetchNewMatchIdsResult fetchResult = matchDataFetcher
                .fetchNewMatchIds(puuid, platform, revisionCheck.dbRevisionDateSeconds(), requestExecutor).join();

        CompletableFuture<List<MatchDto>> matchDetailsFuture = matchDataFetcher
                .fetchMatchDetails(fetchResult.newMatchIds(), platform, requestExecutor);
        CompletableFuture<List<TimelineDto>> timelinesFuture = matchDataFetcher
                .fetchTimelines(fetchResult.newMatchIds(), platform, requestExecutor);

        List<MatchDto> matchDtos = matchDetailsFuture.join();
        List<TimelineDto> timelineDtos = timelinesFuture.join();

        summoner.updateLastRiotCallDate();
        summonerDataCollector.save(summoner);

        if (matchDtos != null && !matchDtos.isEmpty()) {
            saveMatchDataUseCase.execute(matchDtos, timelineDtos);
        }

        log.info("[갱신 완료] puuid={}", puuid);

        if (fetchResult.hasMoreMatches()) {
            log.info("갱신 완료 후 추가 매치 검색 MQ 발행. puuid={}", puuid);
            rabbitTemplate.convertAndSend(
                    RabbitMqBinding.RENEWAL_MATCH_FIND.getExchange(),
                    RabbitMqBinding.RENEWAL_MATCH_FIND.getRoutingKey(),
                    new SummonerRenewalMessage(puuid, platform.getPlatformId(), fetchResult.dbRevisionDateSeconds())
            );
        }
    }
}
