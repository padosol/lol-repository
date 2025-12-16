package com.mmrtr.lol.domain.summoner.service;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.riot.service.RiotApiService;
import com.mmrtr.lol.riot.type.Platform;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private final RiotApiService riotApiService;
    private final SummonerWriter summonerWriter;
    private final Executor requestExecutor;

    @Transactional
    public Summoner getSummonerInfoV2(String regionType, String gameName, String tagLine) {
        Platform platform = Platform.valueOfName(regionType);
        try {
            CompletableFuture<Summoner> summonerFuture = riotApiService
                    .getAccountByRiotId(gameName, tagLine, platform, requestExecutor)
                    .thenCompose(accountDto -> {
                        log.info("getSummonerInfoV2 region type {} and gameName {}", regionType, gameName);
                        String puuid = accountDto.getPuuid();

                        CompletableFuture<SummonerDto> summonerDtoFuture = riotApiService
                                .getSummonerByPuuid(puuid, platform, requestExecutor);
                        CompletableFuture<Set<LeagueEntryDto>> leagueEntriesFuture = riotApiService
                                .getLeagueEntriesByPuuid(puuid, platform, requestExecutor);

                        return summonerDtoFuture
                                .thenCombine(
                                    leagueEntriesFuture,
                                    (summonerDto, leagueEntryDtos) ->
                            Summoner.of(accountDto, summonerDto, platform.getPlatformId(), leagueEntryDtos)
                        );
                    });

            Summoner summoner = summonerFuture.join();
            summonerWriter.saveSummonerData(summoner);

            return summoner;

        } catch (RuntimeException e) {
            log.error("Error fetching summoner info: {}", e.getMessage());
            throw new CoreException(ErrorType.NOT_FOUND_USER, "유저 정보 조회 중 오류가 발생했습니다.");
        }
    }
}
