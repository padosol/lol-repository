package com.mmrtr.lol.infra.rabbitmq.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerDataCollector {

    private final RiotApiService riotApiService;
    private final SummonerAssembler summonerAssembler;
    private final SaveSummonerDataUseCase saveSummonerDataUseCase;

    public SummonerDto fetchSummoner(String puuid, Platform platform, Executor executor) {
        return riotApiService.getSummonerByPuuid(puuid, platform, executor).join();
    }

    public Optional<Summoner> collectAndAssemble(
            String puuid, Platform platform, SummonerDto summonerDto, Executor executor) {

        CompletableFuture<AccountDto> accountFuture = riotApiService
                .getAccountByPuuid(puuid, platform, executor);
        CompletableFuture<Set<LeagueEntryDto>> leagueFuture = riotApiService
                .getLeagueEntriesByPuuid(puuid, platform, executor);

        try {
            Summoner summoner = accountFuture.thenCombine(leagueFuture,
                    (accountDto, leagueEntryDtos) ->
                            summonerAssembler.assemble(accountDto, leagueEntryDtos, summonerDto, platform)
            ).join();
            return Optional.of(summoner);
        } catch (Exception e) {
            if (e.getCause() instanceof RiotClientNotFoundException) {
                log.warn("Account/League 조회 실패. puuid={}", puuid);
                return Optional.empty();
            }
            throw e;
        }
    }

    public void save(Summoner summoner) {
        saveSummonerDataUseCase.execute(summoner);
    }
}
