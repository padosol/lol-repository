package com.mmrtr.lol.domain.summoner.service;

import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.riot.service.RiotApiServiceV2;
import com.mmrtr.lol.riot.type.Platform;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummonerService {

    private final RiotApiServiceV2 riotApiServiceV2;
    private final SummonerWriter summonerWriter;

    @Transactional
    public Summoner getSummonerInfoV2(String regionType, String gameName, String tagLine) {
        log.info("getSummonerInfoV2 region type {} and gameName {}", regionType, gameName);
        Platform platform = Platform.valueOfName(regionType);

        try {
            CompletableFuture<Summoner> summonerFuture = riotApiServiceV2.getAccountByRiotId(gameName, tagLine, platform)
                    .thenCompose(accountDto -> {
                        String puuid = accountDto.getPuuid();

                        CompletableFuture<SummonerDto> summonerDtoFuture = riotApiServiceV2.getSummonerByPuuid(puuid, platform);
                        CompletableFuture<Set<LeagueEntryDto>> leagueEntriesFuture = riotApiServiceV2.getLeagueEntriesByPuuid(puuid, platform);

                        return summonerDtoFuture.thenCombine(leagueEntriesFuture, (summonerDto, leagueEntryDtos) -> {
                            summonerWriter.saveSummonerData(accountDto, summonerDto, leagueEntryDtos, platform);
                            return Summoner.of(accountDto, summonerDto, platform.getPlatformId(), leagueEntryDtos);
                        });
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
