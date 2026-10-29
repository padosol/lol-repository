package com.mmrtr.lol.backfill.incremental;

import com.mmrtr.lol.backfill.BackfillFactType;
import com.mmrtr.lol.backfill.incremental.JobRunOutcome.Status;
import com.mmrtr.lol.backfill.incremental.TickReport.FactOutcome;
import com.mmrtr.lol.domain.championstats.application.usecase.ChampionStatsCacheEvictUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.mmrtr.lol.backfill.BackfillFactType.MATCH;
import static com.mmrtr.lol.backfill.BackfillFactType.MATCH_BAN;
import static com.mmrtr.lol.backfill.BackfillFactType.MATCH_PARTICIPANT_BUILD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatsExportServiceTest {

    private static final List<Integer> QUEUES = List.of(420);
    private static final Instant NOW = Instant.parse("2026-10-30T03:20:00Z");
    private static final Duration MIN_OBSERVE_AGE = Duration.ofMinutes(15);
    private static final Instant OBSERVED_LONG_AGO = NOW.minus(Duration.ofHours(2));

    @Mock RedissonClient redisson;
    @Mock RLock lock;
    @Mock StatsExportProperties props;
    @Mock StaleExecutionCleaner staleCleaner;
    @Mock StatsExportSourceQueries sourceQueries;
    @Mock StatsExportWatermarkRepository repo;
    @Mock StatsExportJobRunner runner;
    @Mock ChampionStatsCacheEvictUseCase evictUseCase;

    private SimpleMeterRegistry registry;
    private StatsExportService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new StatsExportService(redisson, props, staleCleaner, sourceQueries, repo, runner,
                evictUseCase, new StatsExportMetrics(registry), Clock.fixed(NOW, ZoneOffset.UTC));
        given(redisson.getLock("stats-export:lock")).willReturn(lock);
        given(lock.tryLock()).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(props.patchCount()).willReturn(3);
        given(props.queueIds()).willReturn(QUEUES);
        given(props.seedId()).willReturn(0L);
        given(props.minObserveAge()).willReturn(MIN_OBSERVE_AGE);
        given(sourceQueries.latestSeasonPatches(3)).willReturn(List.of(new PatchVersion(16, 21)));
        for (BackfillFactType f : BackfillFactType.values()) {
            given(repo.findOrCreate(f, 0L)).willReturn(new Watermark(f, 0, 0, null, OBSERVED_LONG_AGO, 0));
            given(sourceQueries.nextId(f)).willReturn(0L);
            given(repo.saveRangePatches(eq(f), anyLong(), anyLong(), any())).willReturn(true);
        }
    }

    private void watermark(BackfillFactType f, long start, long end, String patches, long nowMax) {
        watermark(f, start, end, patches, nowMax, OBSERVED_LONG_AGO);
    }

    private void watermark(BackfillFactType f, long start, long end, String patches, long nowMax, Instant observedAt) {
        given(repo.findOrCreate(f, 0L)).willReturn(new Watermark(f, start, end, patches, observedAt, 0));
        given(sourceQueries.nextId(f)).willReturn(nowMax);
    }

    @Test
    @DisplayName("다른 파드가 락을 잡고 있으면 DB 를 건드리지 않고 건너뛴다")
    void runTick_lockHeldElsewhere_skipsWithoutTouchingDb() {
        // given
        given(lock.tryLock()).willReturn(false);

        // when
        TickReport report = service.runTick();

        // then
        assertThat(report.skipped()).isTrue();
        verifyNoInteractions(repo, sourceQueries, runner, staleCleaner, evictUseCase);
    }

    @Test
    @DisplayName("범위가 비면 observed_until 만 nowMax 로 전진")
    void runTick_emptyRange_advancesObservedOnly() {
        // given
        watermark(MATCH, 0, 0, null, 500);

        // when
        TickReport report = service.runTick();

        // then
        verify(repo).advance(MATCH, 0, 0, 500, null);
        verify(runner, never()).run(any());
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.ADVANCED_EMPTY);
    }

    @Test
    @DisplayName("새 범위는 테이블 대상 패치를 고정하고 잡을 돌린 뒤 전진·캐시 무효화")
    void runTick_newRange_freezesTablePatchesAndRunsJob() {
        // given
        watermark(MATCH, 100, 200, null, 300);
        given(sourceQueries.maxPatchInRange(MATCH, 100, 200, QUEUES)).willReturn(Optional.of(new PatchVersion(16, 21)));
        given(runner.run(any())).willReturn(new JobRunOutcome(Status.LOADED, 10, "inc-match-100-200"));

        // when
        TickReport report = service.runTick();

        // then
        verify(repo).saveRangePatches(MATCH, 100, 200, "16.21");
        verify(runner).run(new StatsExportRequest(MATCH, 100, 200, 16, List.of("16.21"), QUEUES));
        verify(repo).advance(MATCH, 100, 200, 300, "inc-match-100-200");
        verify(evictUseCase).evictAll();
        assertThat(report.cacheEvicted()).isTrue();
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.LOADED);
        assertThat(report.facts().get(0).rowsLoaded()).isEqualTo(10);
        assertThat(service.lastReport()).contains(report);
    }

    @Test
    @DisplayName("고정된 range_patches 가 있으면 테이블이 바뀌어도 그 값을 쓴다")
    void runTick_frozenPatches_reusedWithoutQueryingRange() {
        // given
        watermark(MATCH, 100, 200, "16.20", 300);
        given(runner.run(any())).willReturn(new JobRunOutcome(Status.LOADED, 5, "inc-match-100-200"));

        // when
        service.runTick();

        // then
        verify(runner).run(new StatsExportRequest(MATCH, 100, 200, 16, List.of("16.20"), QUEUES));
        verify(sourceQueries, never()).maxPatchInRange(eq(MATCH), anyLong(), anyLong(), any());
        verify(repo, never()).saveRangePatches(eq(MATCH), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("범위에 테이블보다 새 패치가 있으면 보류하고 워터마크를 건드리지 않는다")
    void runTick_rangeNewerThanTable_holdsWatermark() {
        // given
        watermark(MATCH, 100, 200, null, 300);
        given(sourceQueries.maxPatchInRange(MATCH, 100, 200, QUEUES)).willReturn(Optional.of(new PatchVersion(16, 22)));

        // when
        TickReport report = service.runTick();

        // then
        verify(repo, never()).saveRangePatches(eq(MATCH), anyLong(), anyLong(), any());
        verify(repo, never()).advance(eq(MATCH), anyLong(), anyLong(), anyLong(), any());
        verify(runner, never()).run(any());
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.HELD);
        assertThat(registry.counter("stats.export.runs", "fact", "match", "outcome", "held").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("잡이 실패하면 워터마크를 두고 캐시도 비우지 않는다")
    void runTick_jobFails_keepsWatermark() {
        // given
        watermark(MATCH, 100, 200, "16.21", 300);
        given(runner.run(any())).willReturn(new JobRunOutcome(Status.FAILED, 0, "inc-match-100-200"));

        // when
        TickReport report = service.runTick();

        // then
        verify(repo, never()).advance(eq(MATCH), anyLong(), anyLong(), anyLong(), any());
        verify(evictUseCase, never()).evictAll();
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.FAILED);
        assertThat(report.cacheEvicted()).isFalse();
    }

    @Test
    @DisplayName("participant-build 는 기준 데이터가 비면 잡 전에 실패한다")
    void runTick_participantBuildMissingReferenceData_failsBeforeJob() {
        // given
        watermark(MATCH_PARTICIPANT_BUILD, 100, 200, "16.21", 300);
        given(sourceQueries.referenceData(16)).willReturn(new ReferenceDataStatus(0, 3, 3));

        // when
        TickReport report = service.runTick();

        // then
        verify(runner, never()).run(any());
        verify(repo, never()).advance(eq(MATCH_PARTICIPANT_BUILD), anyLong(), anyLong(), anyLong(), any());
        assertThat(report.facts().get(2).outcome()).isEqualTo(FactOutcome.FAILED);
    }

    @Test
    @DisplayName("한 fact 가 예외를 던져도 나머지는 진행한다")
    void runTick_oneFactThrows_othersContinue() {
        // given
        given(repo.findOrCreate(MATCH, 0L)).willThrow(new IllegalStateException("boom"));
        watermark(MATCH_BAN, 0, 0, null, 50);

        // when
        TickReport report = service.runTick();

        // then
        assertThat(report.facts()).hasSize(3);
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.FAILED);
        assertThat(report.facts().get(1).outcome()).isEqualTo(FactOutcome.ADVANCED_EMPTY);
        verify(repo).advance(MATCH_BAN, 0, 0, 50, null);
    }

    @Test
    @DisplayName("대상 패치 행이 없는 범위는 잡 없이 전진")
    void runTick_noTargetRows_advancesWithoutJob() {
        // given
        watermark(MATCH, 100, 200, null, 300);
        given(sourceQueries.maxPatchInRange(MATCH, 100, 200, QUEUES)).willReturn(Optional.of(new PatchVersion(16, 19)));

        // when
        TickReport report = service.runTick();

        // then
        verify(repo).saveRangePatches(MATCH, 100, 200, "");
        verify(repo).advance(MATCH, 100, 200, 300, null);
        verify(runner, never()).run(any());
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.NO_TARGET_ROWS);
    }

    @Test
    @DisplayName("고아 정리가 먼저 돌고, 예외가 나도 락은 풀린다")
    void runTick_cleansStaleFirstAndAlwaysUnlocks() {
        // given
        service.runTick();
        InOrder order = inOrder(staleCleaner, sourceQueries);
        order.verify(staleCleaner).markStaleExecutionsFailed();
        order.verify(sourceQueries).latestSeasonPatches(3);

        given(sourceQueries.latestSeasonPatches(3)).willThrow(new IllegalStateException("db down"));

        // when
        try {
            service.runTick();
        } catch (IllegalStateException expected) {
            // 락 해제만 확인한다
        }

        // then
        verify(lock, org.mockito.Mockito.times(2)).unlock();
    }

    @Test
    @DisplayName("범위 패치 고정에 실패하면 잡도 전진도 없이 실패한다")
    void runTick_saveRangePatchesFails_noJobNoAdvance() {
        // given
        watermark(MATCH, 100, 200, null, 300);
        given(sourceQueries.maxPatchInRange(MATCH, 100, 200, QUEUES)).willReturn(Optional.of(new PatchVersion(16, 21)));
        given(repo.saveRangePatches(MATCH, 100, 200, "16.21")).willReturn(false);

        // when
        TickReport report = service.runTick();

        // then
        verify(runner, never()).run(any());
        verify(repo, never()).advance(eq(MATCH), anyLong(), anyLong(), anyLong(), any());
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.FAILED);
    }

    @Test
    @DisplayName("observed 가 exported 보다 작으면 전진하지 않고 실패한다")
    void runTick_observedBehindExported_failsWithoutAdvance() {
        // given
        watermark(MATCH, 200, 100, null, 300);

        // when
        TickReport report = service.runTick();

        // then
        verify(repo, never()).advance(eq(MATCH), anyLong(), anyLong(), anyLong(), any());
        verify(runner, never()).run(any());
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.FAILED);
    }

    @Test
    @DisplayName("관측한 지 min-observe-age 가 안 된 범위는 패치 고정·잡·전진 없이 연기한다")
    void runTick_rangeYoungerThanMinObserveAge_defersWithoutTouchingRange() {
        // given
        watermark(MATCH, 100, 200, null, 300, NOW.minus(Duration.ofMinutes(14)));

        // when
        TickReport report = service.runTick();

        // then
        verify(sourceQueries, never()).maxPatchInRange(eq(MATCH), anyLong(), anyLong(), any());
        verify(repo, never()).saveRangePatches(eq(MATCH), anyLong(), anyLong(), any());
        verify(repo, never()).advance(eq(MATCH), anyLong(), anyLong(), anyLong(), any());
        verify(runner, never()).run(any());
        verify(evictUseCase, never()).evictAll();
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.DEFERRED);
        assertThat(registry.counter("stats.export.runs", "fact", "match", "outcome", "deferred").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("고정된 range_patches 가 있어도 관측이 너무 최근이면 연기한다")
    void runTick_frozenPatchesButYoungRange_defers() {
        // given
        watermark(MATCH, 100, 200, "16.21", 300, NOW.minusSeconds(1));

        // when
        TickReport report = service.runTick();

        // then
        verify(runner, never()).run(any());
        verify(repo, never()).advance(eq(MATCH), anyLong(), anyLong(), anyLong(), any());
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.DEFERRED);
    }

    @Test
    @DisplayName("관측한 지 정확히 min-observe-age 가 지났으면 진행한다")
    void runTick_rangeExactlyMinObserveAge_proceeds() {
        // given
        watermark(MATCH, 100, 200, null, 300, NOW.minus(MIN_OBSERVE_AGE));
        given(sourceQueries.maxPatchInRange(MATCH, 100, 200, QUEUES)).willReturn(Optional.of(new PatchVersion(16, 21)));
        given(runner.run(any())).willReturn(new JobRunOutcome(Status.LOADED, 10, "inc-match-100-200"));

        // when
        TickReport report = service.runTick();

        // then
        verify(repo).saveRangePatches(MATCH, 100, 200, "16.21");
        verify(runner).run(any());
        verify(repo).advance(MATCH, 100, 200, 300, "inc-match-100-200");
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.LOADED);
    }

    @Test
    @DisplayName("빈 범위는 관측이 최근이어도 observed_until 을 전진한다")
    void runTick_emptyRangeRecentlyObserved_stillAdvances() {
        // given
        watermark(MATCH, 200, 200, null, 500, NOW.minusSeconds(1));

        // when
        TickReport report = service.runTick();

        // then
        verify(repo).advance(MATCH, 200, 200, 500, null);
        assertThat(report.facts().get(0).outcome()).isEqualTo(FactOutcome.ADVANCED_EMPTY);
    }
}
