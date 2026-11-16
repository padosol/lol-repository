package com.mmrtr.lol.infra.persistence.league.scheduler;

import com.mmrtr.lol.common.type.Tier;
import com.mmrtr.lol.domain.league.application.port.LeagueApiPort;
import com.mmrtr.lol.domain.league.application.port.LeagueApiPort.LeagueEntry;
import com.mmrtr.lol.domain.league.application.port.SummonerRankingRepositoryPort;
import com.mmrtr.lol.domain.league.application.port.TierCutoffRepositoryPort;
import com.mmrtr.lol.domain.league.domain.TierCutoff;
import com.mmrtr.lol.domain.summoner.application.SummonerService;
import com.mmrtr.lol.domain.summoner.application.port.SummonerRepositoryPort;
import com.mmrtr.lol.domain.summoner.domain.Summoner;
import com.mmrtr.lol.domain.summoner.domain.vo.GameIdentity;
import com.mmrtr.lol.infra.persistence.match.repository.MatchSummonerJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("소환사 랭킹 계산 서비스")
class SummonerRankingCalculationServiceTest {

    private static final String QUEUE = "RANKED_SOLO_5x5";
    private static final String CHALLENGER_PUUID = "challenger-puuid";
    private static final String GRANDMASTER_PUUID = "grandmaster-puuid";

    @Mock
    private LeagueApiPort leagueApiPort;

    @Mock
    private MatchSummonerJpaRepository matchSummonerJpaRepository;

    @Mock
    private SummonerRankingRepositoryPort summonerRankingRepositoryPort;

    @Mock
    private TierCutoffRepositoryPort tierCutoffRepositoryPort;

    @Mock
    private SummonerRepositoryPort summonerRepositoryPort;

    @Mock
    private SummonerService summonerService;

    @InjectMocks
    private SummonerRankingCalculationService summonerRankingCalculationService;

    @BeforeEach
    void setUp() {
        when(leagueApiPort.getApexEntries(QUEUE, "KR")).thenReturn(Map.of(
                Tier.CHALLENGER, List.of(entry(CHALLENGER_PUUID, 1500)),
                Tier.GRANDMASTER, List.of(entry(GRANDMASTER_PUUID, 700))
        ));
        when(summonerRepositoryPort.findAllByPuuidIn(anyCollection())).thenReturn(Map.of(
                CHALLENGER_PUUID, summoner(CHALLENGER_PUUID, "챌린저"),
                GRANDMASTER_PUUID, summoner(GRANDMASTER_PUUID, "그랜드마스터")
        ));
        when(matchSummonerJpaRepository.findMostChampionsByPuuids(anyList())).thenReturn(List.of());
    }

    @Test
    @DisplayName("커트라인 갱신이 켜지면 랭킹과 함께 티어 커트라인도 교체한다")
    void processQueueRanking_커트라인갱신true_커트라인교체() {
        // when
        summonerRankingCalculationService.processQueueRanking(QUEUE, true);

        // then
        verify(summonerRankingRepositoryPort).replaceAllRankings(eq(QUEUE), anyList());

        verify(tierCutoffRepositoryPort).backupCurrentCutoffs(QUEUE);

        ArgumentCaptor<List<TierCutoff>> captor = ArgumentCaptor.captor();
        verify(tierCutoffRepositoryPort).replaceAllCutoffs(eq(QUEUE), captor.capture());

        List<TierCutoff> cutoffs = captor.getValue();
        assertThat(cutoffs)
                .extracting(TierCutoff::getTier, TierCutoff::getMinLeaguePoints, TierCutoff::getUserCount)
                .containsExactlyInAnyOrder(
                        tuple(Tier.CHALLENGER.name(), 1500, 1),
                        tuple(Tier.GRANDMASTER.name(), 700, 1)
                );
    }

    @Test
    @DisplayName("커트라인 갱신이 꺼지면 랭킹만 교체하고 티어 커트라인은 손대지 않는다")
    void processQueueRanking_커트라인갱신false_커트라인미갱신() {
        // when
        summonerRankingCalculationService.processQueueRanking(QUEUE, false);

        // then
        verify(summonerRankingRepositoryPort).replaceAllRankings(eq(QUEUE), anyList());
        verifyNoInteractions(tierCutoffRepositoryPort);
    }

    private static LeagueEntry entry(String puuid, int leaguePoints) {
        return new LeagueEntry(puuid, leaguePoints, "I", 100, 50, false, false, false, false);
    }

    private static Summoner summoner(String puuid, String gameName) {
        return Summoner.builder()
                .puuid(puuid)
                .gameIdentity(new GameIdentity(gameName, "KR1"))
                .platformId("KR")
                .build();
    }
}
