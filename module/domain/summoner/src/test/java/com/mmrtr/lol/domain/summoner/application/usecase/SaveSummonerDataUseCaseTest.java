package com.mmrtr.lol.domain.summoner.application.usecase;

import com.mmrtr.lol.domain.summoner.application.port.LeagueRepositoryPort;
import com.mmrtr.lol.domain.summoner.application.port.LeagueSummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.LeagueSummoner;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.vo.LeagueInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
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

    @Test
    @DisplayName("티어 체계가 다른 큐(Arena CHERRY 등)도 Riot 이 준 티어 그대로 적재한다")
    void saveLeagues_otherTierSystemQueue_storesRiotValuesAsIs() {
        // given
        LeagueInfo solo = LeagueInfo.builder()
                .leagueId("L1").queueType("RANKED_SOLO_5x5").tier("GOLD").rank("II")
                .leaguePoints(50).wins(10).losses(5).build();
        LeagueInfo arena = LeagueInfo.builder()
                .queueType("CHERRY").tier("WOOD")
                .leaguePoints(0).wins(7).losses(11).build();

        // when
        useCase.saveLeagues("p1", Set.of(solo, arena));

        // then
        ArgumentCaptor<LeagueSummoner> saved = ArgumentCaptor.forClass(LeagueSummoner.class);
        verify(leagueSummonerRepositoryPort, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(LeagueSummoner::getQueue, LeagueSummoner::getTier, LeagueSummoner::getRank)
                .containsExactlyInAnyOrder(
                        tuple("RANKED_SOLO_5x5", "GOLD", "II"),
                        tuple("CHERRY", "WOOD", null));
    }
}
