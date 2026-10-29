package com.mmrtr.lol.domain.league.application;

import com.mmrtr.lol.domain.league.application.port.LeagueApiPort;
import com.mmrtr.lol.domain.league.application.port.SummonerCollectionPublishPort;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotCallScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TierCollectionServiceTest {

    @Test
    @DisplayName("티어 수집의 리그 조회는 BACKGROUND 스코프에서 호출된다")
    void collection_runsUnderBackground() {
        List<RiotCallPriority> seen = new ArrayList<>();
        LeagueApiPort leaguePort = mock(LeagueApiPort.class);
        when(leaguePort.getLeagueEntries(anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> {
                    seen.add(RiotCallScope.current());
                    return Set.<LeagueApiPort.LeagueEntry>of();
                });
        TierCollectionService service = new TierCollectionService(leaguePort, mock(SummonerCollectionPublishPort.class));

        service.execute("KR");

        assertThat(seen).isNotEmpty().allMatch(p -> p == RiotCallPriority.BACKGROUND);
        assertThat(RiotCallScope.current()).isEqualTo(RiotCallPriority.USER);
    }

    @Test
    @DisplayName("수집한 소환사는 사용자 갱신이 아니라 수집용(background)으로 발행한다")
    void collectedSummoners_publishedForCollection() {
        LeagueApiPort leaguePort = mock(LeagueApiPort.class);
        LeagueApiPort.LeagueEntry entry =
                new LeagueApiPort.LeagueEntry("p1", 0, "I", 0, 0, false, false, false, false);
        when(leaguePort.getLeagueEntries(anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Set.<LeagueApiPort.LeagueEntry>of());
        when(leaguePort.getLeagueEntries(anyString(), anyString(), eq("I"), eq(1), anyString()))
                .thenReturn(Set.of(entry));
        SummonerCollectionPublishPort publishPort = mock(SummonerCollectionPublishPort.class);
        TierCollectionService service = new TierCollectionService(leaguePort, publishPort);

        service.execute("KR");

        verify(publishPort).publishForCollection(List.of("p1"), "KR");
        verify(publishPort, never()).publishForRenewal(any(), any());
    }
}
