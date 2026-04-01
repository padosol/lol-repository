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
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerRenewalService {

    private final RiotApiService riotApiService;
    private final SaveMatchDataUseCase saveMatchDataUseCase;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;
    private final Executor requestExecutor;
    private final SummonerRepositoryPort summonerRepositoryPort;
    private final SummonerRevisionChecker summonerRevisionChecker;
    private final MatchDataFetcher matchDataFetcher;
    private final SummonerAssembler summonerAssembler;
    private final RabbitTemplate rabbitTemplate;

    public void renewSummoner(String puuid, Platform platform) {
        log.info("[갱신 시작] puuid={}", puuid);

        SummonerDto summonerDto = riotApiService.getSummonerByPuuid(puuid, platform, requestExecutor).join();
        if (summonerDto == null) {
            log.error("RIOT API에서 소환사 정보를 조회할 수 없습니다. puuid: {}", puuid);
            return;
        }

        RevisionCheckResult revisionCheck = summonerRevisionChecker.check(puuid, summonerDto);
        if (!revisionCheck.needsRenewal()) {
            summonerRepositoryPort.updateLastRiotCallDate(puuid);
            return;
        }

        CompletableFuture<AccountDto> accountDtoFuture = riotApiService
                .getAccountByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<Set<LeagueEntryDto>> leagueEntryDtoFuture = riotApiService
                .getLeagueEntriesByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<FetchNewMatchIdsResult> fetchResultFuture = matchDataFetcher
                .fetchNewMatchIds(puuid, platform, revisionCheck.dbRevisionDateSeconds(), requestExecutor);

        Summoner summoner;
        try {
            summoner = accountDtoFuture.thenCombine(
                    leagueEntryDtoFuture,
                    (accountDto, leagueEntryDtos) ->
                            summonerAssembler.assemble(accountDto, leagueEntryDtos, summonerDto, platform)
            ).join();
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e.getCause() instanceof RiotClientNotFoundException) {
                log.warn("[갱신 스킵] Account 또는 League 정보를 찾을 수 없습니다. puuid={}", puuid);
                return;
            }
            throw e;
        }

        FetchNewMatchIdsResult fetchResult = fetchResultFuture.join();

        CompletableFuture<List<MatchDto>> matchListFuture = fetchResultFuture
                .thenCompose(result -> matchDataFetcher.fetchMatchDetails(result.newMatchIds(), platform, requestExecutor));
        CompletableFuture<List<TimelineDto>> timelineListFuture = fetchResultFuture
                .thenCompose(result -> matchDataFetcher.fetchTimelines(result.newMatchIds(), platform, requestExecutor));

        List<MatchDto> matchDtos = matchListFuture.join();
        List<TimelineDto> timelineDtos = timelineListFuture.join();

        summoner.updateLastRiotCallDate();
        saveSummonerDataUseCase.execute(summoner);

        if (matchDtos != null && !matchDtos.isEmpty()) {
            saveMatchDataUseCase.execute(matchDtos, timelineDtos);
        }

        log.info("[갱신 완료] puuid={}", puuid);

        // 갱신 완료 후 추가 매치 검색 MQ 발행
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
