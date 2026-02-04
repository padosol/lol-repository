package com.mmrtr.lol.infra.riot.adapter;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.RevisionInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.StatusInfo;
import com.mmrtr.lol.domain.summoner.service.port.SummonerApiPort;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.exception.RiotClientException;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerApiAdapter implements SummonerApiPort {

    private final RiotApiService riotApiService;

    @Override
    public CompletableFuture<Summoner> fetchSummonerByRiotId(
            String gameName, String tagLine, String platformName, Executor executor) {
        Platform platform = Platform.valueOfName(platformName);

        return riotApiService.getAccountByRiotId(gameName, tagLine, platform, executor)
                .thenCompose(accountDto -> {
                    log.info("fetchSummonerByRiotId platform {} gameName {}", platformName, gameName);
                    String puuid = accountDto.getPuuid();

                    CompletableFuture<SummonerDto> summonerDtoFuture = riotApiService
                            .getSummonerByPuuid(puuid, platform, executor);
                    CompletableFuture<Set<LeagueEntryDto>> leagueEntriesFuture = riotApiService
                            .getLeagueEntriesByPuuid(puuid, platform, executor);

                    return summonerDtoFuture.thenCombine(
                            leagueEntriesFuture,
                            (summonerDto, leagueEntryDtos) ->
                                    toDomain(accountDto, summonerDto, platform.getPlatformId(), leagueEntryDtos)
                    );
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

                    if (cause instanceof RiotClientException riotEx) {
                        int statusCode = riotEx.getStatus().value();

                        if (statusCode == 429) {
                            throw new CoreException(ErrorType.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요.");
                        }
                        if (statusCode == 404) {
                            throw new CoreException(ErrorType.NOT_FOUND_USER, "유저를 찾을 수 없습니다.");
                        }
                    }

                    throw new CoreException(ErrorType.DEFAULT_ERROR, "유저 정보 조회 중 오류가 발생했습니다.");
                });
    }

    @Override
    public CompletableFuture<Summoner> fetchSummonerByPuuid(
            String puuid, String platformName, Executor executor) {
        Platform platform = Platform.valueOfName(platformName);

        CompletableFuture<AccountDto> accountDtoFuture = riotApiService
                .getAccountByPuuid(puuid, platform, executor);
        CompletableFuture<SummonerDto> summonerDtoFuture = riotApiService
                .getSummonerByPuuid(puuid, platform, executor);
        CompletableFuture<Set<LeagueEntryDto>> leagueEntriesFuture = riotApiService
                .getLeagueEntriesByPuuid(puuid, platform, executor);

        return accountDtoFuture.thenCombine(summonerDtoFuture, (accountDto, summonerDto) ->
                new Object[]{accountDto, summonerDto}
        ).thenCombine(leagueEntriesFuture, (arr, leagueEntryDtos) -> {
            AccountDto accountDto = (AccountDto) arr[0];
            SummonerDto summonerDto = (SummonerDto) arr[1];
            log.debug("fetchSummonerByPuuid platform {} puuid {}", platformName, puuid);
            return toDomain(accountDto, summonerDto, platform.getPlatformId(), leagueEntryDtos);
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

            if (cause instanceof RiotClientException riotEx) {
                int statusCode = riotEx.getStatus().value();

                if (statusCode == 429) {
                    throw new CoreException(ErrorType.TOO_MANY_REQUESTS, "잠시 후 다시 시도해주세요.");
                }
                if (statusCode == 404) {
                    throw new CoreException(ErrorType.NOT_FOUND_USER, "유저를 찾을 수 없습니다.");
                }
            }

            throw new CoreException(ErrorType.DEFAULT_ERROR, "유저 정보 조회 중 오류가 발생했습니다.");
        });
    }

    private Summoner toDomain(AccountDto accountDto,
                              SummonerDto summonerDto,
                              String platformId,
                              Set<LeagueEntryDto> leagueEntryDtos) {
        return Summoner.builder()
                .puuid(accountDto.getPuuid())
                .gameIdentity(new GameIdentity(accountDto.getGameName(), accountDto.getTagLine()))
                .platformId(platformId)
                .statusInfo(new StatusInfo(summonerDto.getProfileIconId(), summonerDto.getSummonerLevel()))
                .revisionInfo(new RevisionInfo(
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(summonerDto.getRevisionDate()), ZoneId.systemDefault()
                        ),
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(summonerDto.getRevisionDate()), ZoneId.systemDefault()
                        )
                ))
                .leagueInfos(toLeagueInfos(leagueEntryDtos))
                .build();
    }

    private Set<LeagueInfo> toLeagueInfos(Set<LeagueEntryDto> leagueEntryDtos) {
        return leagueEntryDtos.stream()
                .map(dto -> LeagueInfo.builder()
                        .leagueId(dto.getLeagueId())
                        .queueType(dto.getQueueType())
                        .tier(dto.getTier())
                        .rank(dto.getRank())
                        .leaguePoints(dto.getLeaguePoints())
                        .wins(dto.getWins())
                        .losses(dto.getLosses())
                        .hotStreak(dto.isHotStreak())
                        .veteran(dto.isVeteran())
                        .freshBlood(dto.isFreshBlood())
                        .inactive(dto.isInactive())
                        .build())
                .collect(Collectors.toSet());
    }
}
