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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
}
