package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.service.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.infra.persistence.match.service.MatchService;
import com.mmrtr.lol.infra.rabbitmq.service.SummonerRevisionChecker.RevisionCheckResult;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.match.MatchDto;
import com.mmrtr.lol.infra.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void renewSummoner(String puuid, Platform platform) {
        long start = System.currentTimeMillis();
        log.info("Lock 획득, 유저 전적 갱신 시작: {}", puuid);

        SummonerDto summonerDto = riotApiService.getSummonerByPuuid(puuid, platform, requestExecutor).join();
        if (summonerDto == null) {
            log.error("RIOT API에서 소환사 정보를 조회할 수 없습니다. puuid: {}", puuid);
            return;
        }

        RevisionCheckResult revisionCheck = summonerRevisionChecker.check(puuid, summonerDto);
        if (!revisionCheck.needsRenewal()) {
            return;
        }

        CompletableFuture<AccountDto> accountDtoFuture = riotApiService
                .getAccountByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<Set<LeagueEntryDto>> leagueEntryDtoFuture = riotApiService
                .getLeagueEntriesByPuuid(puuid, platform, requestExecutor);
        CompletableFuture<List<String>> filteredMatchIdsFuture = matchDataFetcher
                .fetchNewMatchIds(puuid, platform, revisionCheck.dbRevisionDateMillis(), requestExecutor);

        CompletableFuture<List<MatchDto>> matchListFuture = filteredMatchIdsFuture
                .thenCompose(ids -> matchDataFetcher.fetchMatchDetails(ids, platform, requestExecutor));
        CompletableFuture<List<TimelineDto>> timelineListFuture = filteredMatchIdsFuture
                .thenCompose(ids -> matchDataFetcher.fetchTimelines(ids, platform, requestExecutor));

        Summoner summoner = accountDtoFuture.thenCombine(
                leagueEntryDtoFuture,
                (accountDto, leagueEntryDtos) ->
                        summonerAssembler.assemble(accountDto, leagueEntryDtos, summonerDto, platform)
        ).join();

        List<MatchDto> matchDtos = matchListFuture.join();
        List<TimelineDto> timelineDtos = timelineListFuture.join();

        long middle = System.currentTimeMillis();
        log.info("middle {} ms", middle - start);

        summoner.resetClickDate();
        saveSummonerDataUseCase.execute(summoner);

        if (matchDtos != null && !matchDtos.isEmpty()) {
            matchService.addAllMatch(matchDtos, timelineDtos);
        }

        long end = System.currentTimeMillis();
        log.info("소요 시간: {}ms", end - start);
        log.info("Lock 해제, 유저 전적 갱신 완료: {}", puuid);
    }
}
