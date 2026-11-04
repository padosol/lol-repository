package com.mmrtr.lol.domain.summoner.application.usecase;

import com.mmrtr.lol.domain.league.application.port.LeagueRepositoryPort;
import com.mmrtr.lol.domain.league.application.port.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SaveSummonerDataUseCaseTest {

    @Mock
    private SummonerRepositoryPort summonerRepositoryPort;
    @Mock
    private LeagueRepositoryPort leagueRepositoryPort;
    @Mock
    private LeagueSummonerRepositoryPort leagueSummonerRepositoryPort;
    @InjectMocks
    private SaveSummonerDataUseCase useCase;

    @Test
    @DisplayName("리그 저장은 소환사 행을 건드리지 않고 항목마다 LeagueSummoner 를 저장한다")
    void saveLeagues_savesLeagueSummonerPerEntry_withoutTouchingSummoner() {
        // given
        LeagueInfo solo = LeagueInfo.builder()
                .leagueId("L1").queueType("RANKED_SOLO_5x5").tier("GOLD").rank("II")
                .leaguePoints(50).wins(10).losses(5).build();

        // when
        useCase.saveLeagues("p1", Set.of(solo));

        // then
        verify(leagueSummonerRepositoryPort).save(argThat(ls -> ls.getPuuid().equals("p1")));
        verify(leagueRepositoryPort).saveIfAbsent(argThat(l -> l.getLeagueId().equals("L1")));
        verifyNoInteractions(summonerRepositoryPort);
    }
}
