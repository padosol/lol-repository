package com.mmrtr.lol.domain.championstats.application;

import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsCachePort;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsMetricsPort;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsQueryPort;
import com.mmrtr.lol.domain.championstats.domain.TierFilter;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionBootBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionMatchupReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionPositionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRuneBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSkillBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSpellStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStartItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionWinRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;
import com.mmrtr.lol.support.error.CoreException;
import com.mmrtr.lol.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

/** lol-server {@code ChampionStatsServiceTest} 이전 (캐시 OFF 시나리오는 토글을 옮기지 않아 제외) + 입력 검증. */
@ExtendWith(MockitoExtension.class)
class ChampionStatsQueryServiceTest {

    private static final int CHAMPION_ID = 13;
    private static final String PLATFORM = "kr";
    private static final String PLATFORM_ID = "KR";
    private static final String PATCH = "16.1";
    private static final String TIER = "EMERALD";
    private static final TierFilter TIER_FILTER = TierFilter.of(TIER);

    @Mock
    private ChampionStatsQueryPort championStatsQueryPort;

    @Mock
    private ChampionStatsCachePort championStatsCachePort;

    @Mock
    private ChampionStatsMetricsPort championStatsMetricsPort;

    private ChampionStatsQueryService service;

    @BeforeEach
    void setUp() {
        service = new ChampionStatsQueryService(
                championStatsQueryPort, championStatsCachePort, championStatsMetricsPort, Runnable::run);
    }

    @Nested
    @DisplayName("캐시 미스")
    class CacheMissTests {

        @DisplayName("모든 포지션의 챔피언 상세 통계를 그룹핑하여 반환한다")
        @Test
        void getChampionStats_returnsAllPositionStats() {
            // given
            given(championStatsCachePort.tryLockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER)).willReturn(true);
            // by-position 캐시 hit 으로 nested single-flight 단락
            given(championStatsCachePort.findChampionStatsByPosition(PATCH, PLATFORM_ID, TIER))
                    .willReturn(Optional.of(List.of()));

            ChampionWinRateReadModel middleWinRate = new ChampionWinRateReadModel("MIDDLE", 1000, 520, 0.52);
            ChampionWinRateReadModel topWinRate = new ChampionWinRateReadModel("TOP", 200, 110, 0.55);
            given(championStatsQueryPort.getChampionWinRates(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER))
                    .willReturn(List.of(middleWinRate, topWinRate));

            // MIDDLE 포지션 상세 통계
            List<ChampionMatchupReadModel> middleMatchups = List.of(
                    ChampionMatchupReadModel.top(7, 120, 0.5417, 0.12),
                    ChampionMatchupReadModel.top(103, 100, 0.5300, 0.10),
                    ChampionMatchupReadModel.top(4, 80, 0.5250, 0.08),
                    ChampionMatchupReadModel.bottom(238, 150, 0.4667, 0.15),
                    ChampionMatchupReadModel.bottom(91, 130, 0.4692, 0.13),
                    ChampionMatchupReadModel.bottom(55, 90, 0.4778, 0.09));
            given(championStatsQueryPort.getChampionMatchups(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER, "MIDDLE"))
                    .willReturn(middleMatchups);
            given(championStatsQueryPort.getChampionRuneBuilds(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER, "MIDDLE"))
                    .willReturn(List.of(new ChampionRuneBuildReadModel(
                            8100, 8300, 8112, 8139, 8143, 8135, 8304, 8345, 300, 0.5333, 0.6)));
            given(championStatsQueryPort.getChampionSpellStats(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER, "MIDDLE"))
                    .willReturn(List.of(new ChampionSpellStatsReadModel(4, 14, 800, 0.52, 0.8)));
            given(championStatsQueryPort.getChampionSkillBuilds(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER, "MIDDLE"))
                    .willReturn(List.of(new ChampionSkillBuildReadModel("QWEQEEREQEQWWWW", 400, 0.525, 0.4)));
            given(championStatsQueryPort.getChampionStartItemBuilds(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER, "MIDDLE"))
                    .willReturn(List.of(new ChampionStartItemBuildReadModel(List.of(1056, 2003), 600, 0.51, 0.6)));
            given(championStatsQueryPort.getChampionBootBuilds(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER, "MIDDLE"))
                    .willReturn(List.of(new ChampionBootBuildReadModel(3020, 700, 0.53, 0.7)));
            given(championStatsQueryPort.getChampionItemBuilds(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER, "MIDDLE"))
                    .willReturn(List.of(new ChampionItemBuildReadModel(List.of(3089, 3157, 3165), 500, 0.52, 0.5)));

            // when
            ChampionStatsReadModel result = service.getChampionStats(CHAMPION_ID, PLATFORM, PATCH, TIER);

            // then
            assertThat(result.tier()).isEqualTo(TIER);
            assertThat(result.positions()).hasSize(2);

            ChampionPositionStatsReadModel middleStats = result.positions().get(0);
            assertThat(middleStats.teamPosition()).isEqualTo("MIDDLE");
            assertThat(middleStats.winRate()).isEqualTo(0.52);
            assertThat(middleStats.totalGames()).isEqualTo(1000);
            assertThat(middleStats.matchups()).hasSize(6);
            assertThat(middleStats.matchups().get(0).rankType()).isEqualTo("TOP");
            assertThat(middleStats.matchups().get(0).opponentChampionId()).isEqualTo(7);
            assertThat(middleStats.matchups().get(3).rankType()).isEqualTo("BOTTOM");
            assertThat(middleStats.matchups().get(3).opponentChampionId()).isEqualTo(238);
            assertThat(middleStats.runeBuilds()).hasSize(1);
            assertThat(middleStats.spellStats().get(0).summoner1Id()).isEqualTo(4);
            assertThat(middleStats.skillBuilds()).hasSize(1);
            assertThat(middleStats.startItemBuilds().get(0).startItems()).containsExactly(1056, 2003);
            assertThat(middleStats.bootBuilds().get(0).bootId()).isEqualTo(3020);
            assertThat(middleStats.itemBuilds()).hasSize(1);
            // 빌드에는 챔프-라인 총 표본과 Wilson 하한이 붙는다
            assertThat(middleStats.bootBuilds().get(0).totalSampleSize()).isEqualTo(1000);
            assertThat(middleStats.bootBuilds().get(0).confidenceLowerBound())
                    .isEqualTo(WilsonScore.lowerBound95(371, 700));

            ChampionPositionStatsReadModel topStats = result.positions().get(1);
            assertThat(topStats.teamPosition()).isEqualTo("TOP");
            assertThat(topStats.winRate()).isEqualTo(0.55);
            assertThat(topStats.totalGames()).isEqualTo(200);
            assertThat(topStats.matchups()).isEmpty();

            then(championStatsCachePort).should().saveChampionStats(
                    eq(CHAMPION_ID), eq(PATCH), eq(PLATFORM_ID), eq(TIER), any(ChampionStatsReadModel.class));
            then(championStatsCachePort).should().unlockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER);
            then(championStatsMetricsPort).should(never()).recordSingleFlightFallback(anyString());
        }

        @DisplayName("포지션의 픽률·밴률·tier 는 목록 통계에서 같은 챔피언을 찾아 채운다")
        @Test
        void getChampionStats_fillsRatesFromPositionStats() {
            // given
            given(championStatsCachePort.tryLockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER)).willReturn(true);
            given(championStatsCachePort.findChampionStatsByPosition(PATCH, PLATFORM_ID, TIER))
                    .willReturn(Optional.of(List.of(new PositionChampionStatsReadModel("MIDDLE", List.of(
                            new ChampionRateReadModel(7, 0.5, 0.1, 0.2, 0, "A"),
                            new ChampionRateReadModel(CHAMPION_ID, 0.52, 0.08, 0.03, 0, "S+"))))));
            given(championStatsQueryPort.getChampionWinRates(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER))
                    .willReturn(List.of(new ChampionWinRateReadModel("MIDDLE", 1000, 520, 0.52),
                            new ChampionWinRateReadModel("TOP", 200, 110, 0.55)));

            // when
            ChampionStatsReadModel result = service.getChampionStats(CHAMPION_ID, PLATFORM, PATCH, TIER);

            // then
            ChampionPositionStatsReadModel middle = result.positions().get(0);
            assertThat(middle.pickRate()).isEqualTo(0.08);
            assertThat(middle.banRate()).isEqualTo(0.03);
            assertThat(middle.tier()).isEqualTo("S+");
            // 목록 통계에 없는 포지션은 0·null
            ChampionPositionStatsReadModel top = result.positions().get(1);
            assertThat(top.pickRate()).isZero();
            assertThat(top.banRate()).isZero();
            assertThat(top.tier()).isNull();
        }

        @DisplayName("skillBuild 가 null/공백인 항목은 응답에서 제외한다")
        @Test
        void getChampionStats_filtersNullOrBlankSkillBuilds() {
            // given
            given(championStatsCachePort.tryLockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER)).willReturn(true);
            given(championStatsCachePort.findChampionStatsByPosition(PATCH, PLATFORM_ID, TIER))
                    .willReturn(Optional.of(List.of()));
            given(championStatsQueryPort.getChampionWinRates(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER))
                    .willReturn(List.of(new ChampionWinRateReadModel("MIDDLE", 1000, 520, 0.52)));
            given(championStatsQueryPort.getChampionSkillBuilds(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER, "MIDDLE"))
                    .willReturn(List.of(
                            new ChampionSkillBuildReadModel("QWEQEEREQEQWWWW", 400, 0.525, 0.4),
                            new ChampionSkillBuildReadModel(null, 100, 0.5, 0.1),
                            new ChampionSkillBuildReadModel("   ", 50, 0.48, 0.05)));

            // when
            ChampionStatsReadModel result = service.getChampionStats(CHAMPION_ID, PLATFORM, PATCH, TIER);

            // then
            assertThat(result.positions()).hasSize(1);
            assertThat(result.positions().get(0).skillBuilds())
                    .extracting(ChampionSkillBuildReadModel::skillBuild)
                    .containsExactly("QWEQEEREQEQWWWW");
        }

        @DisplayName("승률 데이터가 없으면 빈 포지션 리스트를 반환한다")
        @Test
        void getChampionStats_returnsEmptyPositions_whenNoWinRateData() {
            // given
            given(championStatsCachePort.tryLockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER)).willReturn(true);
            given(championStatsCachePort.findChampionStatsByPosition(PATCH, PLATFORM_ID, TIER))
                    .willReturn(Optional.of(List.of()));
            given(championStatsQueryPort.getChampionWinRates(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER))
                    .willReturn(List.of());

            // when
            ChampionStatsReadModel result = service.getChampionStats(CHAMPION_ID, PLATFORM, PATCH, TIER);

            // then
            assertThat(result.tier()).isEqualTo(TIER);
            assertThat(result.positions()).isEmpty();
            then(championStatsCachePort).should().unlockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER);
        }

        @DisplayName("포지션별 챔피언 승률/픽률/밴률을 반환하고 tier 가 없으면 계산해 채운다")
        @Test
        void getChampionStatsByPosition_returnsGroupedByPosition() {
            // given
            given(championStatsCachePort.tryLockByPosition(PATCH, PLATFORM_ID, TIER)).willReturn(true);
            given(championStatsQueryPort.getChampionStatsByPosition(PATCH, PLATFORM_ID, TIER_FILTER))
                    .willReturn(Map.of(
                            "TOP", List.of(
                                    new ChampionRateReadModel(266, 0.5200, 0.0800, 0.0500, 1500),
                                    new ChampionRateReadModel(122, 0.4800, 0.0600, 0.0300, 1200)),
                            "JUNGLE", List.of(new ChampionRateReadModel(64, 0.5100, 0.1000, 0.0700, 2000))));

            // when
            List<PositionChampionStatsReadModel> result = service.getChampionStatsByPosition(PLATFORM, PATCH, TIER);

            // then
            assertThat(result).extracting(PositionChampionStatsReadModel::teamPosition)
                    .containsExactlyInAnyOrder("TOP", "JUNGLE");
            assertThat(result).flatExtracting(PositionChampionStatsReadModel::champions)
                    .extracting(ChampionRateReadModel::tier)
                    .doesNotContainNull();
            then(championStatsCachePort).should().saveChampionStatsByPosition(eq(PATCH), eq(PLATFORM_ID), eq(TIER), any());
            then(championStatsCachePort).should().unlockByPosition(PATCH, PLATFORM_ID, TIER);
        }

        @DisplayName("BigQuery 가 정한 tier 는 그대로 둔다")
        @Test
        void getChampionStatsByPosition_keepsBigQueryTier() {
            // given
            given(championStatsCachePort.tryLockByPosition(PATCH, PLATFORM_ID, TIER)).willReturn(true);
            given(championStatsQueryPort.getChampionStatsByPosition(PATCH, PLATFORM_ID, TIER_FILTER))
                    .willReturn(Map.of("TOP", List.of(
                            new ChampionRateReadModel(266, 0.52, 0.08, 0.05, 1500, "B"),
                            new ChampionRateReadModel(122, 0.48, 0.06, 0.03, 1200, "S+"))));

            // when
            List<PositionChampionStatsReadModel> result = service.getChampionStatsByPosition(PLATFORM, PATCH, TIER);

            // then
            assertThat(result.get(0).champions()).extracting(ChampionRateReadModel::tier).containsExactly("B", "S+");
        }

        @DisplayName("포지션별 챔피언 통계 조회 시 데이터가 없으면 빈 리스트를 반환한다")
        @Test
        void getChampionStatsByPosition_returnsEmptyList_whenNoData() {
            // given
            given(championStatsCachePort.tryLockByPosition(PATCH, PLATFORM_ID, TIER)).willReturn(true);
            given(championStatsQueryPort.getChampionStatsByPosition(PATCH, PLATFORM_ID, TIER_FILTER))
                    .willReturn(Map.of());

            // when & then
            assertThat(service.getChampionStatsByPosition(PLATFORM, PATCH, TIER)).isEmpty();
        }
    }

    @Nested
    @DisplayName("캐시 히트")
    class CacheHitTests {

        @DisplayName("캐시 히트 시 QueryPort 와 락을 호출하지 않는다")
        @Test
        void getChampionStats_cacheHit_skipsQueryPortAndLock() {
            // given
            ChampionStatsReadModel cached = new ChampionStatsReadModel(TIER, List.of());
            given(championStatsCachePort.findChampionStats(CHAMPION_ID, PATCH, PLATFORM_ID, TIER))
                    .willReturn(Optional.of(cached));

            // when
            ChampionStatsReadModel result = service.getChampionStats(CHAMPION_ID, PLATFORM, PATCH, TIER);

            // then
            assertThat(result).isSameAs(cached);
            verifyNoInteractions(championStatsQueryPort);
            then(championStatsCachePort).should(never()).tryLockDetail(anyInt(), anyString(), anyString(), anyString());
        }

        @DisplayName("포지션별 캐시 히트 시 QueryPort 와 락을 호출하지 않는다 — 빈 목록도 히트")
        @Test
        void getChampionStatsByPosition_cacheHit_skipsQueryPortAndLock() {
            // given
            List<PositionChampionStatsReadModel> cached = List.of();
            given(championStatsCachePort.findChampionStatsByPosition(PATCH, PLATFORM_ID, TIER))
                    .willReturn(Optional.of(cached));

            // when
            List<PositionChampionStatsReadModel> result = service.getChampionStatsByPosition(PLATFORM, PATCH, TIER);

            // then
            assertThat(result).isSameAs(cached);
            verifyNoInteractions(championStatsQueryPort);
            then(championStatsCachePort).should(never()).tryLockByPosition(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Single-flight")
    class SingleFlightTests {

        @DisplayName("락 획득 실패 후 폴링 중 캐시 hit 발생 시 BQ 를 호출하지 않는다")
        @Test
        void getChampionStats_lockFailedThenCachePopulated_skipsBigQuery() {
            // given
            ChampionStatsReadModel populated = new ChampionStatsReadModel(TIER, List.of());
            given(championStatsCachePort.findChampionStats(CHAMPION_ID, PATCH, PLATFORM_ID, TIER))
                    .willReturn(Optional.empty()).willReturn(Optional.of(populated));
            given(championStatsCachePort.tryLockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER)).willReturn(false);

            // when
            ChampionStatsReadModel result = service.getChampionStats(CHAMPION_ID, PLATFORM, PATCH, TIER);

            // then
            assertThat(result).isSameAs(populated);
            verifyNoInteractions(championStatsQueryPort, championStatsMetricsPort);
        }

        @DisplayName("락 획득 실패 + 폴링 timeout → fallback BQ 호출 + 캐시 set + counter 증가")
        @Test
        void getChampionStats_lockFailedAndPollExhausted_fallsBackAndRecordsMetric() {
            // given
            given(championStatsCachePort.tryLockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER)).willReturn(false);
            given(championStatsCachePort.findChampionStatsByPosition(PATCH, PLATFORM_ID, TIER))
                    .willReturn(Optional.of(List.of()));
            given(championStatsQueryPort.getChampionWinRates(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER))
                    .willReturn(List.of());

            // when (인터럽트로 폴링 대기를 건너뛴다)
            Thread.currentThread().interrupt();
            ChampionStatsReadModel result;
            try {
                result = service.getChampionStats(CHAMPION_ID, PLATFORM, PATCH, TIER);
            } finally {
                Thread.interrupted();
            }

            // then
            assertThat(result.tier()).isEqualTo(TIER);
            then(championStatsQueryPort).should().getChampionWinRates(CHAMPION_ID, PATCH, PLATFORM_ID, TIER_FILTER);
            then(championStatsCachePort).should().saveChampionStats(
                    eq(CHAMPION_ID), eq(PATCH), eq(PLATFORM_ID), eq(TIER), any(ChampionStatsReadModel.class));
            then(championStatsMetricsPort).should().recordSingleFlightFallback(ChampionStatsMetricsPort.ENDPOINT_DETAIL);
            then(championStatsCachePort).should(never()).unlockDetail(anyInt(), anyString(), anyString(), anyString());
        }

        @DisplayName("락 획득 후 double-check 캐시 hit 시 BQ 를 호출하지 않고 unlock 한다")
        @Test
        void getChampionStats_lockAcquiredButCachePopulatedDuringWait_skipsBigQuery() {
            // given
            ChampionStatsReadModel populated = new ChampionStatsReadModel(TIER, List.of());
            given(championStatsCachePort.findChampionStats(CHAMPION_ID, PATCH, PLATFORM_ID, TIER))
                    .willReturn(Optional.empty()).willReturn(Optional.of(populated));
            given(championStatsCachePort.tryLockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER)).willReturn(true);

            // when
            ChampionStatsReadModel result = service.getChampionStats(CHAMPION_ID, PLATFORM, PATCH, TIER);

            // then
            assertThat(result).isSameAs(populated);
            verifyNoInteractions(championStatsQueryPort);
            then(championStatsCachePort).should(never()).saveChampionStats(
                    anyInt(), anyString(), anyString(), anyString(), any());
            then(championStatsCachePort).should().unlockDetail(CHAMPION_ID, PATCH, PLATFORM_ID, TIER);
        }

        @DisplayName("BigQuery 실패는 그대로 던지고 락은 푼다 — 캐시에 남기지 않는다")
        @Test
        void getChampionStatsByPosition_queryFails_unlocksWithoutSaving() {
            // given
            given(championStatsCachePort.tryLockByPosition(PATCH, PLATFORM_ID, TIER)).willReturn(true);
            given(championStatsQueryPort.getChampionStatsByPosition(PATCH, PLATFORM_ID, TIER_FILTER))
                    .willThrow(new IllegalStateException("bigquery down"));

            // when & then
            assertThatThrownBy(() -> service.getChampionStatsByPosition(PLATFORM, PATCH, TIER))
                    .isInstanceOf(IllegalStateException.class);
            then(championStatsCachePort).should().unlockByPosition(PATCH, PLATFORM_ID, TIER);
            then(championStatsCachePort).should(never()).saveChampionStatsByPosition(anyString(), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class ValidationTests {

        @DisplayName("platform 은 이름으로 받아 Riot platformId 로, 범위 tier 는 표기 그대로 캐시 키에 쓴다")
        @Test
        void getChampionStatsByPosition_convertsPlatformAndKeepsTierDisplay() {
            // given
            given(championStatsCachePort.findChampionStatsByPosition("14.24", "EUW1", "MASTER+"))
                    .willReturn(Optional.of(List.of()));

            // when
            service.getChampionStatsByPosition("euw", "14.24", "MASTER+");

            // then
            then(championStatsCachePort).should().findChampionStatsByPosition("14.24", "EUW1", "MASTER+");
        }

        @DisplayName("모르는 platform 은 INVALID_INPUT")
        @Test
        void unknownPlatform_throwsInvalidInput() {
            assertThatThrownBy(() -> service.getChampionStatsByPosition("mars", PATCH, TIER))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.INVALID_INPUT);
            verifyNoInteractions(championStatsCachePort, championStatsQueryPort);
        }

        @DisplayName("patch 가 major.minor 형식이 아니면 INVALID_INPUT (lol-server 는 500)")
        @ParameterizedTest
        @ValueSource(strings = {"latest", "14", "14.24.1", "14.x", "14.100", ""})
        void invalidPatch_throwsInvalidInput(String patch) {
            assertThatThrownBy(() -> service.getChampionStats(CHAMPION_ID, PLATFORM, patch, TIER))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.INVALID_INPUT);
            verifyNoInteractions(championStatsCachePort, championStatsQueryPort);
        }

        @DisplayName("모르는 tier 는 INVALID_TIER_FILTER")
        @ParameterizedTest
        @ValueSource(strings = {"UNRANKED", "emerald", "MASTER++", " "})
        void invalidTier_throwsInvalidTierFilter(String tier) {
            assertThatThrownBy(() -> service.getChampionStatsByPosition(PLATFORM, PATCH, tier))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.INVALID_TIER_FILTER);
            verifyNoInteractions(championStatsCachePort, championStatsQueryPort);
        }
    }
}
