package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.match.application.port.MatchCacheWritePort;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
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
    private final MatchCacheWritePort matchCacheWritePort;
    private final AsyncMatchSaver asyncMatchSaver;
    private final RabbitTemplate rabbitTemplate;
    private final Executor requestExecutor;

    public void renewSummoner(String puuid, Platform platform) {
        log.info("[갱신 시작] puuid={}", puuid);
        long renewalStart = System.currentTimeMillis();
        try {
            doRenew(puuid, platform);
        } finally {
            log.info("[갱신 완료] puuid={} 총 소요: {}ms", puuid, System.currentTimeMillis() - renewalStart);
        }
    }

    private void doRenew(String puuid, Platform platform) {
        long t = System.currentTimeMillis();
        SummonerDto summonerDto = summonerDataCollector.fetchSummoner(puuid, platform, requestExecutor);
        log.info("[갱신-API] fetchSummoner: {}ms puuid={}", System.currentTimeMillis() - t, puuid);
        if (summonerDto == null) {
            log.error("RIOT API에서 소환사 정보를 조회할 수 없습니다. puuid: {}", puuid);
            return;
        }

        RevisionCheckResult revisionCheck = summonerRevisionChecker.check(puuid, summonerDto);
        if (!revisionCheck.needsRenewal()) {
            summonerRepositoryPort.updateLastRiotCallDate(puuid);
            return;
        }

        t = System.currentTimeMillis();
        Optional<Summoner> summonerOpt = summonerDataCollector
                .collectAndAssemble(puuid, platform, summonerDto, requestExecutor);
        log.info("[갱신-API] collectAndAssemble: {}ms puuid={}", System.currentTimeMillis() - t, puuid);
        if (summonerOpt.isEmpty()) {
            return;
        }

        Summoner summoner = summonerOpt.get();
        t = System.currentTimeMillis();
        FetchNewMatchIdsResult fetchResult = matchDataFetcher
                .fetchNewMatchIds(puuid, platform, revisionCheck.dbRevisionDateSeconds(), requestExecutor).join();
        log.info("[갱신-API] fetchNewMatchIds: {}ms count={} puuid={}",
                System.currentTimeMillis() - t, fetchResult.newMatchIds().size(), puuid);

        t = System.currentTimeMillis();
        CompletableFuture<List<MatchDto>> matchDetailsFuture = matchDataFetcher
                .fetchMatchDetails(fetchResult.newMatchIds(), platform, requestExecutor);
        CompletableFuture<List<TimelineDto>> timelinesFuture = matchDataFetcher
                .fetchTimelines(fetchResult.newMatchIds(), platform, requestExecutor);

        List<MatchDto> matchDtos = matchDetailsFuture.join();
        List<TimelineDto> timelineDtos = timelinesFuture.join();
        log.info("[갱신-API] fetchMatchDetails+Timelines (parallel): {}ms count={} puuid={}",
                System.currentTimeMillis() - t, matchDtos.size(), puuid);

        summoner.updateLastRiotCallDate();
        summonerDataCollector.save(summoner);

        if (matchDtos != null && !matchDtos.isEmpty()) {
            matchCacheWritePort.writeMatches(matchDtos, timelineDtos, puuid);
            asyncMatchSaver.saveAsync(matchDtos, timelineDtos);
        }

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
