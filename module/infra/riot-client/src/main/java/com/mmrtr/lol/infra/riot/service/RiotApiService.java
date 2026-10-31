package com.mmrtr.lol.infra.riot.service;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.champion.ChampionInfo;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueListDto;
import com.mmrtr.lol.domain.match.readmodel.MatchDto;
import com.mmrtr.lol.domain.match.readmodel.timeline.TimelineDto;
import com.mmrtr.lol.infra.riot.dto.spectator.CurrentGameInfoVO;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.ratelimit.RiotRequestAttributes;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
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
        log.debug("getAccountByRiotId gameName {} tagLine {}", gameName, tagLine);
        // 공개 API 입력이라 템플릿 변수로 넘겨 / ? # { } 까지 인코딩한다 (경로에 그대로 붙이면 다른 Riot 경로를 부를 수 있다)
        String path = "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}";
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path, gameName, tagLine)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "account-v1.by-riot-id")
                                .retrieve()
                                .body(AccountDto.class)
                , executor
        );
    }

    public CompletableFuture<AccountDto> getAccountByPuuid(String puuid, Platform platform, Executor executor) {
        String path = String.format("/riot/account/v1/accounts/by-puuid/%s", puuid);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "account-v1.by-puuid")
                                .retrieve()
                                .body(AccountDto.class)
                , executor
        );
    }

    public CompletableFuture<SummonerDto> getSummonerByPuuid(String puuid, Platform platform, Executor executor) {
        String path = String.format("/lol/summoner/v4/summoners/by-puuid/%s", puuid);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "summoner-v4.by-puuid")
                                .retrieve()
                                .body(SummonerDto.class)
                , executor
        );
    }

    public CompletableFuture<Set<LeagueEntryDto>> getLeagueEntriesByPuuid(String puuid, Platform platform, Executor executor) {
        String path = String.format("/lol/league/v4/entries/by-puuid/%s", puuid);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "league-v4.entries-by-puuid")
                                .retrieve()
                                .body(new ParameterizedTypeReference<Set<LeagueEntryDto>>() {})
                , executor
        );
    }

    public CompletableFuture<SummonerDto> getSummonerByName(String summonerName, Platform platform, Executor executor) {
        String path = String.format("/lol/summoner/v4/summoners/by-name/%s", summonerName);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "summoner-v4.by-name")
                                .retrieve()
                                .body(SummonerDto.class)
                , executor
        );
    }

    public CompletableFuture<List<String>> getMatchListByPuuid(String puuid, Platform platform, Executor executor) {
        String path = String.format("/lol/match/v5/matches/by-puuid/%s/ids", puuid);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "match-v5.ids-by-puuid")
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<String>>() {})
                , executor
        );
    }

    public CompletableFuture<List<String>> getMatchListByPuuid(
            String puuid, Platform platform, long startTime, int start, int count, Executor executor) {
        String path = String.format("/lol/match/v5/matches/by-puuid/%s/ids", puuid);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path, uriBuilder -> uriBuilder
                                        .queryParam("startTime", startTime)
                                        .queryParam("start", start)
                                        .queryParam("count", count).build())
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "match-v5.ids-by-puuid")
                                .retrieve()
                                .body(new ParameterizedTypeReference<List<String>>() {})
                , executor
        );
    }

    public CompletableFuture<MatchDto> getMatchById(String matchId, Platform platform, Executor executor) {
        URI uri = URI.create(platform.getRegionalHost() + "/lol/match/v5/matches/" + matchId);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(uri)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "match-v5.match")
                                .retrieve()
                                .body(MatchDto.class)
                , executor
        );
    }

    public CompletableFuture<TimelineDto> getTimelineById(String matchId, Platform platform, Executor executor) {
        String path = String.format("/lol/match/v5/matches/%s/timeline", matchId);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getRegionalHost() + path)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "match-v5.timeline")
                                .retrieve()
                                .body(TimelineDto.class)
                , executor
        );
    }

    public Set<LeagueEntryDto> getLeagueEntries(
            String queue, String tier, String division, int page, Platform platform) {
        String path = String.format("/lol/league/v4/entries/%s/%s/%s", queue, tier, division);
        RiotCallPriority priority = RiotCallScope.current();
        return riotRestClient.get()
                .uri(platform.getPlatformHost() + path, uriBuilder ->
                        uriBuilder.queryParam("page", page).build())
                .attribute(RiotRequestAttributes.PRIORITY, priority)
                .attribute(RiotRequestAttributes.METHOD, "league-v4.entries")
                .retrieve()
                .body(new ParameterizedTypeReference<Set<LeagueEntryDto>>() {});
    }

    public CompletableFuture<LeagueListDto> getApexLeague(
            String tierPath, String queue, Platform platform, Executor executor) {
        String path = String.format("/lol/league/v4/%s/by-queue/%s", tierPath, queue);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "league-v4." + tierPath)
                                .retrieve()
                                .body(LeagueListDto.class),
                executor
        );
    }

    public CompletableFuture<ChampionInfo> getChampionRotation(Platform platform, Executor executor) {
        String path = "/lol/platform/v3/champion-rotations";

        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "champion-v3.rotations")
                                .retrieve()
                                .body(ChampionInfo.class)
                , executor
        );
    }

    public CompletableFuture<CurrentGameInfoVO> getActiveGameByPuuid(
            String puuid, Platform platform, Executor executor) {
        String path = String.format("/lol/spectator/v5/active-games/by-summoner/%s", puuid);
        // supplyAsync 는 다른 스레드에서 돌아 ThreadLocal 스코프가 사라지므로 호출 스레드에서 먼저 읽는다
        RiotCallPriority priority = RiotCallScope.current();
        return CompletableFuture.supplyAsync(() ->
                        riotRestClient.get()
                                .uri(platform.getPlatformHost() + path)
                                .attribute(RiotRequestAttributes.PRIORITY, priority)
                                .attribute(RiotRequestAttributes.METHOD, "spectator-v5.active-games")
                                .retrieve()
                                .body(CurrentGameInfoVO.class)
                , executor
        );
    }
}
