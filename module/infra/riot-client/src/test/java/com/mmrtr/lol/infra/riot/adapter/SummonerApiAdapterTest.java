package com.mmrtr.lol.infra.riot.adapter;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import com.mmrtr.lol.domain.summoner.domain.vo.SummonerProfile;
import com.mmrtr.lol.infra.riot.dto.account.AccountDto;
import com.mmrtr.lol.infra.riot.dto.league.LeagueEntryDto;
import com.mmrtr.lol.infra.riot.dto.summoner.SummonerDto;
import com.mmrtr.lol.infra.riot.exception.RiotClientNotFoundException;
import com.mmrtr.lol.infra.riot.exception.RiotRateLimitException;
import com.mmrtr.lol.infra.riot.service.RiotApiService;
import com.mmrtr.lol.support.riot.RiotPermit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.http.HttpStatus;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SummonerApiAdapterTest {

    private static final Executor DIRECT = Runnable::run;
    private static final Platform PLATFORM = Platform.KR;

    private final RiotApiService riotApiService = mock(RiotApiService.class);
    private final RiotPermit permit = mock(RiotPermit.class);
    private final SummonerApiAdapter adapter = new SummonerApiAdapter(riotApiService);

    @Test
    void 계정_응답을_GameIdentity로_바꾼다() {
        AccountDto dto = new AccountDto();
        dto.setGameName("Hide");
        dto.setTagLine("KR1");
        when(riotApiService.getAccountByPuuid("puuid", PLATFORM, permit, DIRECT))
                .thenReturn(CompletableFuture.completedFuture(dto));

        GameIdentity result = adapter.fetchGameIdentity("puuid", PLATFORM, permit, DIRECT).join();

        assertThat(result).isEqualTo(new GameIdentity("Hide", "KR1"));
    }

    @Test
    void 소환사_응답을_SummonerProfile로_바꾼다() {
        SummonerDto dto = new SummonerDto();
        dto.setProfileIconId(29);
        dto.setSummonerLevel(512);
        dto.setRevisionDate(1700000000000L);
        when(riotApiService.getSummonerByPuuid("puuid", PLATFORM, permit, DIRECT))
                .thenReturn(CompletableFuture.completedFuture(dto));

        SummonerProfile result = adapter.fetchSummonerProfile("puuid", PLATFORM, permit, DIRECT).join();

        assertThat(result).isEqualTo(new SummonerProfile(29, 512, 1700000000000L));
    }

    @Test
    void 리그_엔트리를_LeagueInfo로_바꾼다() {
        LeagueEntryDto dto = new LeagueEntryDto();
        dto.setLeagueId("league-1");
        dto.setQueueType("RANKED_SOLO_5x5");
        dto.setTier("GOLD");
        dto.setRank("II");
        dto.setLeaguePoints(55);
        dto.setWins(30);
        dto.setLosses(20);
        dto.setHotStreak(true);
        dto.setVeteran(true);
        dto.setFreshBlood(true);
        dto.setInactive(true);
        when(riotApiService.getLeagueEntriesByPuuid("puuid", PLATFORM, permit, DIRECT))
                .thenReturn(CompletableFuture.completedFuture(Set.of(dto)));

        Set<LeagueInfo> result = adapter.fetchLeagueInfos("puuid", PLATFORM, permit, DIRECT).join();

        assertThat(result).hasSize(1);
        LeagueInfo info = result.iterator().next();
        assertThat(info.getLeagueId()).isEqualTo("league-1");
        assertThat(info.getQueueType()).isEqualTo("RANKED_SOLO_5x5");
        assertThat(info.getTier()).isEqualTo("GOLD");
        assertThat(info.getRank()).isEqualTo("II");
        assertThat(info.getLeaguePoints()).isEqualTo(55);
        assertThat(info.getWins()).isEqualTo(30);
        assertThat(info.getLosses()).isEqualTo(20);
        assertThat(info.isHotStreak()).isTrue();
        assertThat(info.isVeteran()).isTrue();
        assertThat(info.isFreshBlood()).isTrue();
        assertThat(info.isInactive()).isTrue();
    }

    @Test
    void Riot_예외를_감싸지_않는다() {
        RiotClientNotFoundException notFound =
                new RiotClientNotFoundException(HttpStatus.NOT_FOUND, "{}", LogLevel.DEBUG);
        when(riotApiService.getLeagueEntriesByPuuid("puuid", PLATFORM, permit, DIRECT))
                .thenReturn(CompletableFuture.failedFuture(notFound));
        RiotRateLimitException rateLimit = mock(RiotRateLimitException.class);
        when(riotApiService.getSummonerByPuuid("puuid", PLATFORM, permit, DIRECT))
                .thenReturn(CompletableFuture.failedFuture(rateLimit));

        assertThatThrownBy(() -> adapter.fetchLeagueInfos("puuid", PLATFORM, permit, DIRECT).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseReference(notFound);
        assertThatThrownBy(() -> adapter.fetchSummonerProfile("puuid", PLATFORM, permit, DIRECT).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseReference(rateLimit);
    }
}
