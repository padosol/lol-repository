package com.mmrtr.lol.infra.riot.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.champion.ChampionInfo;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.match.MatchDto;
import com.mmrtr.lol.infra.riot.dto.match_timeline.TimelineDto;
import com.mmrtr.lol.infra.riot.dto.spectator.CurrentGameInfoVO;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiotApiService {

    private final RestClient riotRestClient;

    public CompletableFuture<AccountDto> getAccountByRiotId(
            String gameName, String tagLine, Platform platform, Executor executor) {
        log.info("getAccountByRiotId gameName {} tagLine {}", gameName, tagLine);
        String path = String.format("/riot/account/v1/accounts/by-riot-id/%s/%s", gameName, tagLine);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .retrieve()
                                .body(AccountDto.class)
                , executor
        );
    }

    public CompletableFuture<AccountDto> getAccountByPuuid(String puuid, Platform platform, Executor executor) {
        String path = String.format("/riot/account/v1/accounts/by-puuid/%s", puuid);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .retrieve()
                                .body(AccountDto.class)
                , executor
        );
    }

    public CompletableFuture<SummonerDto> getSummonerByPuuid(String puuid, Platform platform, Executor executor) {
        String path = String.format("/lol/summoner/v4/summoners/by-puuid/%s", puuid);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .retrieve()
                                .body(SummonerDto.class)
                , executor
        );
    }

    public CompletableFuture<Set<LeagueEntryDto>> getLeagueEntriesByPuuid(String puuid, Platform platform, Executor executor) {
        String path = String.format("/lol/league/v4/entries/by-puuid/%s", puuid);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .retrieve()
                                .body(new ParameterizedTypeReference<Set<LeagueEntryDto>>() {})
                , executor
        );
    }

    public CompletableFuture<SummonerDto> getSummonerByName(String summonerName, Platform platform, Executor executor) {
        String path = String.format("/lol/summoner/v4/summoners/by-name/%s", summonerName);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .retrieve()
                                .body(SummonerDto.class)
                , executor
        );
    }

    public CompletableFuture<List<String>> getMatchListByPuuid(String puuid, Platform platform, Executor executor) {
        String path = String.format("/lol/match/v5/matches/by-puuid/%s/ids", puuid);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<String>>() {})
                , executor
        );
    }

    public CompletableFuture<List<String>> getMatchListByPuuid(
            String puuid, Platform platform, long startTime, int start, int count, Executor executor) {
        String path = String.format("/lol/match/v5/matches/by-puuid/%s/ids", puuid);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path, uriBuilder -> uriBuilder
                                        .queryParam("startTime", startTime)
                                        .queryParam("start", start)
                                        .queryParam("count", count).build())
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<String>>() {})
                , executor
        );
    }

    public CompletableFuture<MatchDto> getMatchById(String matchId, Platform platform, Executor executor) {
        URI uri = URI.create(platform.getRegionalHost() + "/lol/match/v5/matches/" + matchId);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(uri)
                                .retrieve()
                                .body(MatchDto.class)
                , executor
        );
    }

    public CompletableFuture<TimelineDto> getTimelineById(String matchId, Platform platform, Executor executor) {
        String path = String.format("/lol/match/v5/matches/%s/timeline", matchId);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .retrieve()
                                .body(TimelineDto.class)
                , executor
        );
    }

    public CompletableFuture<ChampionInfo> getChampionRotation(Platform platform, Executor executor) {
        String path = "/lol/platform/v3/champion-rotations";

        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .retrieve()
                                .body(ChampionInfo.class)
                , executor
        );
    }

    public CompletableFuture<CurrentGameInfoVO> getActiveGameByPuuid(
            String puuid, Platform platform, Executor executor) {
        String path = String.format("/lol/spectator/v5/active-games/by-summoner/%s", puuid);
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .retrieve()
                                .body(CurrentGameInfoVO.class)
                , executor
        );
    }
}
