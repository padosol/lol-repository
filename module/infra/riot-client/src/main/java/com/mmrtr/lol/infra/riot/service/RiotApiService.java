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
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
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

/**
 * Riot API 호출. 메서드마다 그 호출의 {@link RiotPermit} 을 받아야 부를 수 있다 (허가 없이는 컴파일되지 않는다).
 * permit 의 method·platform 이 이 호출과 다르면 보내지 않고 {@link IllegalArgumentException} 이다 (호출 스레드에서 던진다).
 * 보내고 받는 일은 {@link RiotCallRecorder} 가 하며, 실패해도 다시 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiotApiService {

    private static final ParameterizedTypeReference<Set<LeagueEntryDto>> LEAGUE_ENTRIES =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<String>> MATCH_IDS = new ParameterizedTypeReference<>() {};

    private final RestClient riotRestClient;
    private final RiotCallRecorder recorder;

    public CompletableFuture<AccountDto> getAccountByRiotId(
            String gameName, String tagLine, Platform platform, RiotPermit permit, Executor executor) {
        log.debug("getAccountByRiotId gameName {} tagLine {}", gameName, tagLine);
        String host = hostFor(permit, RiotMethod.ACCOUNT_BY_RIOT_ID, platform);
        // 공개 API 입력이라 템플릿 변수로 넘겨 / ? # { } 까지 인코딩한다 (경로에 그대로 붙이면 다른 Riot 경로를 부를 수 있다)
        String path = "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}";
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path, gameName, tagLine)
                        .retrieve()
                        .toEntity(AccountDto.class)), executor);
    }

    public CompletableFuture<AccountDto> getAccountByPuuid(
            String puuid, Platform platform, RiotPermit permit, Executor executor) {
        String host = hostFor(permit, RiotMethod.ACCOUNT_BY_PUUID, platform);
        String path = String.format("/riot/account/v1/accounts/by-puuid/%s", puuid);
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path)
                        .retrieve()
                        .toEntity(AccountDto.class)), executor);
    }

    public CompletableFuture<SummonerDto> getSummonerByPuuid(
            String puuid, Platform platform, RiotPermit permit, Executor executor) {
        String host = hostFor(permit, RiotMethod.SUMMONER_BY_PUUID, platform);
        String path = String.format("/lol/summoner/v4/summoners/by-puuid/%s", puuid);
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path)
                        .retrieve()
                        .toEntity(SummonerDto.class)), executor);
    }

    public CompletableFuture<Set<LeagueEntryDto>> getLeagueEntriesByPuuid(
            String puuid, Platform platform, RiotPermit permit, Executor executor) {
        String host = hostFor(permit, RiotMethod.LEAGUE_ENTRIES_BY_PUUID, platform);
        String path = String.format("/lol/league/v4/entries/by-puuid/%s", puuid);
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path)
                        .retrieve()
                        .toEntity(LEAGUE_ENTRIES)), executor);
    }

    public CompletableFuture<List<String>> getMatchListByPuuid(
            String puuid, Platform platform, long startTime, int start, int count, RiotPermit permit,
            Executor executor) {
        String host = hostFor(permit, RiotMethod.MATCH_IDS_BY_PUUID, platform);
        String path = String.format("/lol/match/v5/matches/by-puuid/%s/ids", puuid);
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path, uriBuilder -> uriBuilder
                                .queryParam("startTime", startTime)
                                .queryParam("start", start)
                                .queryParam("count", count).build())
                        .retrieve()
                        .toEntity(MATCH_IDS)), executor);
    }

    public CompletableFuture<MatchDto> getMatchById(
            String matchId, Platform platform, RiotPermit permit, Executor executor) {
        String host = hostFor(permit, RiotMethod.MATCH, platform);
        URI uri = URI.create(host + "/lol/match/v5/matches/" + matchId);
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(uri)
                        .retrieve()
                        .toEntity(MatchDto.class)), executor);
    }

    public CompletableFuture<TimelineDto> getTimelineById(
            String matchId, Platform platform, RiotPermit permit, Executor executor) {
        String host = hostFor(permit, RiotMethod.MATCH_TIMELINE, platform);
        String path = String.format("/lol/match/v5/matches/%s/timeline", matchId);
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path)
                        .retrieve()
                        .toEntity(TimelineDto.class)), executor);
    }

    /** 동기 호출. 부른 스레드에서 보낸다. */
    public Set<LeagueEntryDto> getLeagueEntries(
            String queue, String tier, String division, int page, Platform platform, RiotPermit permit) {
        String host = hostFor(permit, RiotMethod.LEAGUE_ENTRIES, platform);
        String path = String.format("/lol/league/v4/entries/%s/%s/%s", queue, tier, division);
        return recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path, uriBuilder -> uriBuilder.queryParam("page", page).build())
                        .retrieve()
                        .toEntity(LEAGUE_ENTRIES));
    }

    /** apexMethod 는 CHALLENGER_LEAGUE 또는 GRANDMASTER_LEAGUE 다. 그 밖이면 IllegalArgumentException. */
    public CompletableFuture<LeagueListDto> getApexLeague(
            RiotMethod apexMethod, String queue, Platform platform, RiotPermit permit, Executor executor) {
        String tierPath = apexTierPath(apexMethod);
        String host = hostFor(permit, apexMethod, platform);
        String path = String.format("/lol/league/v4/%s/by-queue/%s", tierPath, queue);
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path)
                        .retrieve()
                        .toEntity(LeagueListDto.class)), executor);
    }

    public CompletableFuture<ChampionInfo> getChampionRotation(Platform platform, RiotPermit permit, Executor executor) {
        String host = hostFor(permit, RiotMethod.CHAMPION_ROTATION, platform);
        String path = "/lol/platform/v3/champion-rotations";
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path)
                        .retrieve()
                        .toEntity(ChampionInfo.class)), executor);
    }

    public CompletableFuture<CurrentGameInfoVO> getActiveGameByPuuid(
            String puuid, Platform platform, RiotPermit permit, Executor executor) {
        String host = hostFor(permit, RiotMethod.ACTIVE_GAME, platform);
        String path = String.format("/lol/spectator/v5/active-games/by-summoner/%s", puuid);
        return CompletableFuture.supplyAsync(() -> recorder.exchange(permit, host, () ->
                riotRestClient.get()
                        .uri(host + path)
                        .retrieve()
                        .toEntity(CurrentGameInfoVO.class)), executor);
    }

    /** permit 이 이 호출(method·platform)의 것인지 확인하고, method 의 routing 에 맞는 Riot 호스트를 돌려준다. */
    private static String hostFor(RiotPermit permit, RiotMethod method, Platform platform) {
        if (permit == null || permit.method() != method) {
            throw new IllegalArgumentException("이 호출의 허가가 아닙니다: 필요한 method=" + method
                    + ", 받은 permit=" + permit);
        }
        if (platform == null || Platform.valueOfName(permit.platform()) != platform) {
            throw new IllegalArgumentException("이 platform 의 허가가 아닙니다: platform=" + platform
                    + ", 받은 permit=" + permit);
        }
        return method.routing() == RiotMethod.Routing.REGIONAL ? platform.getRegionalHost() : platform.getPlatformHost();
    }

    private static String apexTierPath(RiotMethod apexMethod) {
        if (apexMethod == RiotMethod.CHALLENGER_LEAGUE) {
            return "challengerleagues";
        }
        if (apexMethod == RiotMethod.GRANDMASTER_LEAGUE) {
            return "grandmasterleagues";
        }
        throw new IllegalArgumentException("apex 리그 method 가 아닙니다: " + apexMethod);
    }
}
