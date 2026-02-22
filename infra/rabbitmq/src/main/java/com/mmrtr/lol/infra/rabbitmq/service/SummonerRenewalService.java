package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.service.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.infra.persistence.match.service.MatchService;
import com.mmrtr.lol.infra.rabbitmq.config.RabbitMqBinding;
import com.mmrtr.lol.infra.rabbitmq.dto.SummonerRenewalMessage;
import com.mmrtr.lol.infra.rabbitmq.service.MatchDataFetcher.FetchNewMatchIdsResult;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRevisionChecker.RevisionCheckResult;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.match.MatchDto;
import com.mmrtr.lol.infra.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
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
    private final MatchService matchService;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;
    private final Executor requestExecutor;
    private final SummonerRevisionChecker summonerRevisionChecker;
    private final MatchDataFetcher matchDataFetcher;
    private final SummonerAssembler summonerAssembler;
    private final RabbitTemplate rabbitTemplate;

    public void renewSummoner(String puuid, Platform platform) {
        long totalStart = System.currentTimeMillis();
        log.info("[갱신 시작] puuid={}", puuid);

        // 1) Summoner 조회 (RIOT API)
        long t = System.currentTimeMillis();
        SummonerDto summonerDto = riotApiService.getSummonerByPuuid(puuid, platform, requestExecutor).join();
        log.debug("[1/6] getSummonerByPuuid: {}ms", System.currentTimeMillis() - t);

        if (summonerDto == null) {
            log.error("RIOT API에서 소환사 정보를 조회할 수 없습니다. puuid: {}", puuid);
            return;
        }

        // 2) Revision 체크 (DB 조회)
        t = System.currentTimeMillis();
        RevisionCheckResult revisionCheck = summonerRevisionChecker.check(puuid, summonerDto);
        log.debug("[2/6] revisionCheck: {}ms", System.currentTimeMillis() - t);

        if (!revisionCheck.needsRenewal()) {
            return;
        }

        // 3) 비동기 요청 시작 (Account + League + MatchIds)
        t = System.currentTimeMillis();
        CompletableFuture<AccountDto> accountDtoFuture = riotApiService
                .getAccountByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<Set<LeagueEntryDto>> leagueEntryDtoFuture = riotApiService
                .getLeagueEntriesByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<FetchNewMatchIdsResult> fetchResultFuture = matchDataFetcher
                .fetchNewMatchIds(puuid, platform, revisionCheck.dbRevisionDateMillis(), requestExecutor);

        CompletableFuture<List<MatchDto>> matchListFuture = fetchResultFuture
                .thenCompose(result -> matchDataFetcher.fetchMatchDetails(result.newMatchIds(), platform, requestExecutor));
        CompletableFuture<List<TimelineDto>> timelineListFuture = fetchResultFuture
                .thenCompose(result -> matchDataFetcher.fetchTimelines(result.newMatchIds(), platform, requestExecutor));

        // 4) Account + League join
        Summoner summoner = accountDtoFuture.thenCombine(
                leagueEntryDtoFuture,
                (accountDto, leagueEntryDtos) ->
                        summonerAssembler.assemble(accountDto, leagueEntryDtos, summonerDto, platform)
        ).join();
        log.debug("[3/6] account+league API: {}ms", System.currentTimeMillis() - t);

        // 5) Match + Timeline join
        long t2 = System.currentTimeMillis();
        FetchNewMatchIdsResult fetchResult = fetchResultFuture.join();
        List<MatchDto> matchDtos = matchListFuture.join();
        List<TimelineDto> timelineDtos = timelineListFuture.join();
        log.debug("[4/6] matchDetails+timelines API: {}ms (매치 {}건)", System.currentTimeMillis() - t2, matchDtos.size());

        // 6) Summoner 저장
        t = System.currentTimeMillis();
        summoner.resetClickDate();
        saveSummonerDataUseCase.execute(summoner);
        log.debug("[5/6] saveSummonerData: {}ms", System.currentTimeMillis() - t);

        // 7) Match 저장
        if (matchDtos != null && !matchDtos.isEmpty()) {
            t = System.currentTimeMillis();
            matchService.addAllMatch(matchDtos, timelineDtos);
            log.debug("[6/6] addAllMatch: {}ms", System.currentTimeMillis() - t);
        }

        log.info("[갱신 완료] puuid={}, 총 소요: {}ms", puuid, System.currentTimeMillis() - totalStart);

        // 8) 갱신 완료 후 추가 매치 검색 MQ 발행
        if (fetchResult.hasMoreMatches()) {
            log.info("갱신 완료 후 추가 매치 검색 MQ 발행. puuid={}", puuid);
            rabbitTemplate.convertAndSend(
                    RabbitMqBinding.RENEWAL_MATCH_FIND.getExchange(),
                    RabbitMqBinding.RENEWAL_MATCH_FIND.getRoutingKey(),
                    new SummonerRenewalMessage(puuid, platform.getPlatformId(), fetchResult.dbRevisionDateMillis())
            );
        }
    }
}
