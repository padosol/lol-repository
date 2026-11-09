package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.domain.summoner.application.port.SummonerApiPort;
import com.mmrtr.lol.domain.summoner.application.usecase.SaveSummonerDataUseCase;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SummonerServiceTest {

    private final SummonerApiPort summonerApiPort = mock(SummonerApiPort.class);
    private final SaveSummonerDataUseCase saveSummonerDataUseCase = mock(SaveSummonerDataUseCase.class);
    private final RiotPermitPort riotPermitPort = mock(RiotPermitPort.class);
    private final SummonerService service =
            new SummonerService(summonerApiPort, saveSummonerDataUseCase, riotPermitPort, Runnable::run);

    @BeforeEach
    void setUp() {
        given(riotPermitPort.tryAcquire(anyString(), any(), anyMap())).willAnswer(SummonerServiceTest::grant);
        given(riotPermitPort.acquireWithRetry(anyString(), any(), anyMap(), anyInt(), any()))
                .willAnswer(invocation -> grant(invocation));
        given(summonerApiPort.fetchSummonerByRiotId(anyString(), anyString(), anyString(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(Summoner.builder().puuid("p1").build()));
        given(summonerApiPort.fetchSummonerByPuuid(anyString(), anyString(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(Summoner.builder().puuid("p1").build()));
    }

    @Test
    @DisplayName("Riot ID 조회는 USER 로 Riot ID 조회 비용의 허가를 받아 그 허가로 부른다")
    void getSummonerInfoV2_acquiresUserPermitsForRiotIdLookup() {
        // when
        service.getSummonerInfoV2("kr", "hide on bush", "KR1");

        // then
        verify(riotPermitPort).tryAcquire("kr", RiotCallPriority.USER, SummonerApiPort.BY_RIOT_ID_COST);
        verify(summonerApiPort).fetchSummonerByRiotId(eq("hide on bush"), eq("KR1"), eq("kr"),
                argThat(permits -> permits.remaining(RiotMethod.ACCOUNT_BY_RIOT_ID) == 1), any());
    }

    @Test
    @DisplayName("USER puuid 조회는 한 번만 묻는다")
    void getSummonerByPuuid_user_asksOnce() {
        // when
        service.getSummonerByPuuid("KR", "p1", RiotCallPriority.USER);

        // then
        verify(riotPermitPort).tryAcquire("KR", RiotCallPriority.USER, SummonerApiPort.BY_PUUID_COST);
        verify(riotPermitPort, never()).acquireWithRetry(anyString(), any(), anyMap(), anyInt(), any());
    }

    @Test
    @DisplayName("BACKGROUND puuid 조회는 1초 간격으로 10번까지 다시 묻는 허가로 부른다")
    void getSummonerByPuuid_background_acquiresWithRetry() {
        // when
        service.getSummonerByPuuid("KR", "p1", RiotCallPriority.BACKGROUND);

        // then
        verify(riotPermitPort).acquireWithRetry(
                "KR", RiotCallPriority.BACKGROUND, SummonerApiPort.BY_PUUID_COST, 10, Duration.ofSeconds(1));
        verify(summonerApiPort).fetchSummonerByPuuid(eq("p1"), eq("KR"),
                argThat(permits -> permits.remaining(RiotMethod.ACCOUNT_BY_PUUID) == 1), any());
    }

    @Test
    @DisplayName("허가가 거절되면 Riot 을 부르지 않고 TOO_MANY_REQUESTS 를 던진다")
    void deniedThrowsTooManyRequests() {
        // given
        given(riotPermitPort.tryAcquire(anyString(), any(), anyMap())).willReturn(Optional.empty());

        // when // then
        assertThatThrownBy(() -> service.getSummonerByPuuid("KR", "p1", RiotCallPriority.USER))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> service.getSummonerInfoV2("KR", "hide on bush", "KR1"))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.TOO_MANY_REQUESTS));
        verifyNoInteractions(summonerApiPort, saveSummonerDataUseCase);
    }

    @Test
    @DisplayName("BACKGROUND 는 다시 물어도 거절이면 Riot 을 부르지 않고 던져 호출한 쪽이 그 puuid 를 건너뛰게 한다")
    void schedulerRetriesThenSkips() {
        // given
        given(riotPermitPort.acquireWithRetry(anyString(), any(), anyMap(), anyInt(), any()))
                .willReturn(Optional.empty());

        // when // then
        assertThatThrownBy(() -> service.getSummonerByPuuid("KR", "p1", RiotCallPriority.BACKGROUND))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.TOO_MANY_REQUESTS));
        verifyNoInteractions(summonerApiPort, saveSummonerDataUseCase);
    }

    private static Optional<RiotPermits> grant(InvocationOnMock invocation) {
        String platform = invocation.getArgument(0);
        RiotCallPriority priority = invocation.getArgument(1);
        Map<RiotMethod, Integer> counts = invocation.getArgument(2);
        List<RiotPermit> permits = new ArrayList<>();
        counts.forEach((method, count) -> {
            for (int k = 1; k <= count; k++) {
                permits.add(new RiotPermit("t-" + method + "-" + k, platform, method, priority));
            }
        });
        return Optional.of(RiotPermits.of(permits));
    }
}
