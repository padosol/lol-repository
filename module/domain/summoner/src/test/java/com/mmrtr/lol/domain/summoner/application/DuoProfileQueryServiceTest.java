package com.mmrtr.lol.domain.summoner.application;

import com.mmrtr.lol.common.type.Queue;
import com.mmrtr.lol.domain.summoner.application.port.DuoProfileQueryPort;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.MostChampion;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.PlayedChampion;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RecentGame;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.RecentGameSummary;
import com.mmrtr.lol.domain.summoner.readmodel.DuoProfileReadModel.TierInfo;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DuoProfileQueryServiceTest {

    private static final String PUUID = "puuid-1";

    @Mock
    private DuoProfileQueryPort duoProfileQueryPort;

    @InjectMocks
    private DuoProfileQueryService service;

    @DisplayName("솔로 랭크 티어·모스트 3·최근 20게임을 모아 승패를 센다")
    @Test
    void getDuoProfile_indexedSummoner_aggregatesSoloRankStats() {
        // given
        given(duoProfileQueryPort.existsSummoner(PUUID)).willReturn(true);
        given(duoProfileQueryPort.findTier(PUUID, Queue.RANKED_SOLO_5x5))
                .willReturn(Optional.of(new TierInfo("GOLD", "II", 45)));
        List<MostChampion> mostChampions = List.of(new MostChampion(103, "Ahri", 12, 8, 4));
        given(duoProfileQueryPort.findMostChampions(PUUID, Queue.RANKED_SOLO_5x5, 3)).willReturn(mostChampions);
        given(duoProfileQueryPort.findRecentGames(PUUID, Queue.RANKED_SOLO_5x5, 20)).willReturn(List.of(
                new RecentGame(true, 103, "Ahri"),
                new RecentGame(false, 238, "Zed"),
                new RecentGame(true, 103, "Ahri")));

        // when
        DuoProfileReadModel profile = service.getDuoProfile(PUUID);

        // then
        assertThat(profile.tierInfo()).isEqualTo(new TierInfo("GOLD", "II", 45));
        assertThat(profile.mostChampions()).isEqualTo(mostChampions);
        assertThat(profile.recentGameSummary()).isEqualTo(new RecentGameSummary(2, 1, List.of(
                new PlayedChampion(103, "Ahri"), new PlayedChampion(238, "Zed"), new PlayedChampion(103, "Ahri"))));
    }

    @DisplayName("솔로 랭크 기록·게임이 없으면 UNRANKED(I, 0LP)·빈 목록·0승 0패")
    @Test
    void getDuoProfile_noSoloRankData_returnsUnrankedAndEmpty() {
        // given
        given(duoProfileQueryPort.existsSummoner(PUUID)).willReturn(true);
        given(duoProfileQueryPort.findTier(PUUID, Queue.RANKED_SOLO_5x5)).willReturn(Optional.empty());
        given(duoProfileQueryPort.findMostChampions(PUUID, Queue.RANKED_SOLO_5x5, 3)).willReturn(List.of());
        given(duoProfileQueryPort.findRecentGames(PUUID, Queue.RANKED_SOLO_5x5, 20)).willReturn(List.of());

        // when
        DuoProfileReadModel profile = service.getDuoProfile(PUUID);

        // then
        assertThat(profile.tierInfo()).isEqualTo(new TierInfo("UNRANKED", "I", 0));
        assertThat(profile.mostChampions()).isEmpty();
        assertThat(profile.recentGameSummary()).isEqualTo(new RecentGameSummary(0, 0, List.of()));
    }

    @DisplayName("색인된 적 없는 소환사는 언랭과 구분해 NOT_FOUND_PUUID(404), 통계는 조회하지 않는다")
    @Test
    void getDuoProfile_notIndexedSummoner_throwsNotFoundPuuid() {
        // given
        given(duoProfileQueryPort.existsSummoner(PUUID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> service.getDuoProfile(PUUID))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.NOT_FOUND_PUUID);
        verify(duoProfileQueryPort, never()).findTier(anyString(), any());
        verify(duoProfileQueryPort, never()).findMostChampions(anyString(), any(), anyInt());
        verify(duoProfileQueryPort, never()).findRecentGames(anyString(), any(), anyInt());
    }
}
