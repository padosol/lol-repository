package com.mmrtr.lol.riot.service;

import com.mmrtr.lol.riot.config.RiotAPIProperties;
import com.mmrtr.lol.riot.dto.account.AccountDto;
import com.mmrtr.lol.riot.dto.champion.ChampionInfo;
import com.mmrtr.lol.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.riot.dto.match.MatchDto;
import com.mmrtr.lol.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lol.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.riot.type.Platform;
import com.mmrtr.lol.support.error.RiotServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiotApiService {

    private final RestClient riotRestClient;
    private final RiotAPIProperties riotAPIProperties;
    private final Executor asyncExecutor;

    public CompletableFuture<AccountDto> getAccountByRiotId(String gameName, String tagLine, Platform platform) {
        log.info("getAccountByRiotId gameName {} tagLine {}", gameName, tagLine);
        String path = String.format("/riot/account/v1/accounts/by-riot-id/%s/%s", gameName, tagLine);
        return CompletableFuture.supplyAsync(() -> {
                    return executeWithRetry(() ->
                            riotRestClient.get()
                                    .uri(platform.getRegionalHost() + path)
                                    .retrieve()
                                    .body(AccountDto.class)
                    );
                }, asyncExecutor
        );
    }

    public CompletableFuture<AccountDto> getAccountByPuuid(String puuid, Platform platform) {
        String path = String.format("/riot/account/v1/accounts/by-puuid/%s", puuid);
        return CompletableFuture.supplyAsync(() ->
                executeWithRetry(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .retrieve()
                                .body(AccountDto.class)
                ), asyncExecutor
        );
    }

    public CompletableFuture<SummonerDto> getSummonerByPuuid(String puuid, Platform platform) {
        String path = String.format("/lol/summoner/v4/summoners/by-puuid/%s", puuid);
        return CompletableFuture.supplyAsync(() ->
                executeWithRetry(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .retrieve()
                                .body(SummonerDto.class)
                ), asyncExecutor
        );
    }

    public CompletableFuture<Set<LeagueEntryDto>> getLeagueEntriesByPuuid(String puuid, Platform platform) {
        String path = String.format("/lol/league/v4/entries/by-puuid/%s", puuid);
        return CompletableFuture.supplyAsync(() ->
                executeWithRetry(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .retrieve()
                                .body(new ParameterizedTypeReference<Set<LeagueEntryDto>>() {})
                ), asyncExecutor
        );
    }

    public CompletableFuture<SummonerDto> getSummonerByName(String summonerName, Platform platform) {
        String path = String.format("/lol/summoner/v4/summoners/by-name/%s", summonerName);
        return CompletableFuture.supplyAsync(() ->
                executeWithRetry(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .retrieve()
                                .body(SummonerDto.class)
                ), asyncExecutor
        );
    }

    public CompletableFuture<List<String>> getMatchListByPuuid(String puuid, Platform platform) {
        String path = String.format("/lol/match/v5/matches/by-puuid/%s/ids", puuid);
        return CompletableFuture.supplyAsync(() ->
                executeWithRetry(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<String>>() {})
                ), asyncExecutor
        );
    }

    public CompletableFuture<List<String>> getMatchListByPuuid(
            String puuid, Platform platform, long startTime, int start, int count) {
        String path = String.format("/lol/match/v5/matches/by-puuid/%s/ids", puuid);
        return CompletableFuture.supplyAsync(() ->
                executeWithRetry(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path, uriBuilder -> uriBuilder
                                        .queryParam("startTime", startTime)
                                        .queryParam("start", start)
                                        .queryParam("count", count).build())
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<String>>() {})
                ), asyncExecutor
        );
    }

    public CompletableFuture<MatchDto> getMatchById(String matchId, Platform platform) {
        URI uri = URI.create(platform.getRegionalHost() + "/lol/match/v5/matches/" + matchId);
        return CompletableFuture.supplyAsync(() ->
                executeWithRetry(() ->
                        riotRestClient.get()
                                .uri(uri)
                                .retrieve()
                                .body(MatchDto.class)
                ), asyncExecutor
        );
    }

    public CompletableFuture<TimelineDto> getTimelineById(String matchId, Platform platform) {
        String path = String.format("/lol/match/v5/matches/%s/timeline", matchId);
        return CompletableFuture.supplyAsync(() ->
                executeWithRetry(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .retrieve()
                                .body(TimelineDto.class)
                ), asyncExecutor
        );
    }

    public CompletableFuture<ChampionInfo> getChampionRotation(Platform platform) {
        String path = "/lol/platform/v3/champion-rotations";
        return CompletableFuture.supplyAsync(() ->
                executeWithRetry(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .retrieve()
                                .body(ChampionInfo.class)
                ), asyncExecutor
        );
    }

    private <T> T executeWithRetry(Supplier<T> supplier) {
        for (int i = 0; i < riotAPIProperties.getRetryAttempts(); i++) {
            try {
                return supplier.get();
            } catch (RiotServerException e) {
                log.warn("Riot API server error. Retrying... ({}/{})", i + 1, riotAPIProperties.getRetryAttempts());
                try {
                    Thread.sleep(riotAPIProperties.getRetryDelay() * 1000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "Retry interrupted.");
                }
            }
        }
        throw new RiotServerException(HttpStatus.SERVICE_UNAVAILABLE, "Riot API server is not responding after " + riotAPIProperties.getRetryAttempts() + " retries.");
    }
}
