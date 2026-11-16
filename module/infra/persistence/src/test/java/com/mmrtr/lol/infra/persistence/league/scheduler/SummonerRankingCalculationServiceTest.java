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
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

    @Mock
    private RiotPermitPort riotPermitPort;

    @InjectMocks
    private SummonerRankingCalculationService summonerRankingCalculationService;

    @BeforeEach
    void setUp() {
        when(riotPermitPort.acquireWithRetry(anyString(), any(), anyMap(), anyInt(), any())).thenAnswer(inv -> {
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
        });
        // 허가 거절 테스트는 Riot·DB 조회까지 가지 않으므로 lenient 로 둔다
        lenient().when(leagueApiPort.getApexEntries(eq(QUEUE), eq("KR"), any())).thenReturn(Map.of(
                Tier.CHALLENGER, List.of(entry(CHALLENGER_PUUID, 1500)),
                Tier.GRANDMASTER, List.of(entry(GRANDMASTER_PUUID, 700))
        ));
        lenient().when(summonerRepositoryPort.findAllByPuuidIn(anyCollection())).thenReturn(Map.of(
                CHALLENGER_PUUID, summoner(CHALLENGER_PUUID, "챌린저"),
                GRANDMASTER_PUUID, summoner(GRANDMASTER_PUUID, "그랜드마스터")
        ));
        lenient().when(matchSummonerJpaRepository.findMostChampionsByPuuids(anyList())).thenReturn(List.of());
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

    @Test
    @DisplayName("Riot 호출(최상위 리그·미등록 소환사 조회)은 BACKGROUND 허가로 한다 — 최상위 리그는 APEX_COST 를 한 번에 받는다")
    void processQueueRanking_riotCallsUnderBackground() {
        // given
        when(summonerRepositoryPort.findAllByPuuidIn(anyCollection())).thenReturn(
                new HashMap<>(Map.of(CHALLENGER_PUUID, summoner(CHALLENGER_PUUID, "챌린저"))));
        when(summonerService.getSummonerByPuuid("KR", GRANDMASTER_PUUID, RiotCallPriority.BACKGROUND))
                .thenReturn(summoner(GRANDMASTER_PUUID, "그랜드마스터"));

        // when
        summonerRankingCalculationService.processQueueRanking(QUEUE, false);

        // then
        verify(riotPermitPort).acquireWithRetry(
                "KR", RiotCallPriority.BACKGROUND, LeagueApiPort.APEX_COST, 10, Duration.ofSeconds(1));
        ArgumentCaptor<RiotPermits> permits = ArgumentCaptor.forClass(RiotPermits.class);
        verify(leagueApiPort).getApexEntries(eq(QUEUE), eq("KR"), permits.capture());
        assertThat(permits.getValue().remaining(RiotMethod.CHALLENGER_LEAGUE)).isEqualTo(1);
        assertThat(permits.getValue().remaining(RiotMethod.GRANDMASTER_LEAGUE)).isEqualTo(1);
        verify(summonerService).getSummonerByPuuid("KR", GRANDMASTER_PUUID, RiotCallPriority.BACKGROUND);
    }

    @Test
    @DisplayName("최상위 리그 허가를 다시 물어도 거절이면 이번 계산을 건너뛴다 — Riot 도 DB 도 건드리지 않는다")
    void schedulerRetriesThenSkips() {
        // given
        when(riotPermitPort.acquireWithRetry(anyString(), any(), anyMap(), anyInt(), any()))
                .thenReturn(Optional.empty());

        // when
        summonerRankingCalculationService.processQueueRanking(QUEUE, false);

        // then
        verify(leagueApiPort, never()).getApexEntries(anyString(), anyString(), any());
        verify(summonerRankingRepositoryPort, never()).replaceAllRankings(anyString(), anyList());
        verifyNoInteractions(summonerRankingRepositoryPort, tierCutoffRepositoryPort);
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
