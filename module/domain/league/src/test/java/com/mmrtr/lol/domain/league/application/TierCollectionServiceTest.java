package com.mmrtr.lol.domain.league.application;

import com.mmrtr.lol.common.type.Division;
import com.mmrtr.lol.domain.league.application.port.LeagueApiPort;
import com.mmrtr.lol.domain.league.application.port.SummonerCollectionPublishPort;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TierCollectionServiceTest {

    private final LeagueApiPort leaguePort = mock(LeagueApiPort.class);
    private final SummonerCollectionPublishPort publishPort = mock(SummonerCollectionPublishPort.class);
    private final RiotPermitPort permitPort = mock(RiotPermitPort.class);
    private final TierCollectionService service = new TierCollectionService(leaguePort, publishPort, permitPort);

    @BeforeEach
    void setUp() {
        when(permitPort.acquireWithRetry(anyString(), any(), anyMap(), anyInt(), any()))
                .thenAnswer(TierCollectionServiceTest::grant);
        when(leaguePort.getLeagueEntries(anyString(), anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(Set.of());
    }

    @Test
    @DisplayName("티어 수집은 페이지마다 BACKGROUND 로 리그 항목 허가 1건을 받아 그 허가로 부른다")
    void collection_takesBackgroundPermitPerPage() {
        // given
        List<RiotPermits> passed = new ArrayList<>();
        when(leaguePort.getLeagueEntries(anyString(), anyString(), anyString(), anyInt(), anyString(), any()))
                .thenAnswer(inv -> {
                    passed.add(inv.getArgument(5));
                    return Set.<LeagueApiPort.LeagueEntry>of();
                });

        // when
        service.execute("KR");

        // then
        int pages = Division.values().length;
        verify(permitPort, times(pages)).acquireWithRetry(
                "KR", RiotCallPriority.BACKGROUND, Map.of(RiotMethod.LEAGUE_ENTRIES, 1), 10, Duration.ofSeconds(1));
        assertThat(passed).hasSize(pages)
                .allSatisfy(permits -> assertThat(permits.remaining(RiotMethod.LEAGUE_ENTRIES)).isEqualTo(1));
    }

    @Test
    @DisplayName("다시 물어도 거절이면 Riot 을 부르지 않고 예외 없이 수집을 멈춘다")
    void schedulerRetriesThenSkips() {
        // given
        when(permitPort.acquireWithRetry(anyString(), any(), anyMap(), anyInt(), any())).thenReturn(Optional.empty());

        // when
        service.execute("KR");

        // then
        verify(permitPort).acquireWithRetry(
                "KR", RiotCallPriority.BACKGROUND, Map.of(RiotMethod.LEAGUE_ENTRIES, 1), 10, Duration.ofSeconds(1));
        verify(leaguePort, never()).getLeagueEntries(anyString(), anyString(), anyString(), anyInt(), anyString(), any());
        verify(publishPort, never()).publishForCollection(any(), any());
    }

    @Test
    @DisplayName("수집한 소환사는 사용자 갱신이 아니라 수집용(background)으로 발행한다")
    void collectedSummoners_publishedForCollection() {
        // given
        LeagueApiPort.LeagueEntry entry =
                new LeagueApiPort.LeagueEntry("p1", 0, "I", 0, 0, false, false, false, false);
        when(leaguePort.getLeagueEntries(anyString(), anyString(), eq("I"), eq(1), anyString(), any()))
                .thenReturn(Set.of(entry));

        // when
        service.execute("KR");

        // then
        verify(publishPort).publishForCollection(List.of("p1"), "KR");
        verify(publishPort, never()).publishForRenewal(any(), any(), any());
    }

    /** 요청한 건수만큼 허가를 준다. */
    private static Optional<RiotPermits> grant(InvocationOnMock inv) {
        String platform = inv.getArgument(0);
        RiotCallPriority priority = inv.getArgument(1);
        Map<RiotMethod, Integer> counts = inv.getArgument(2);
        List<RiotPermit> permits = new ArrayList<>();
        counts.forEach((method, count) -> {
            for (int k = 1; k <= count; k++) {
                permits.add(new RiotPermit("t-" + method + "-" + k, platform, method, priority));
            }
        });
        return Optional.of(RiotPermits.of(permits));
    }
}
