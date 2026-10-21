package com.mmrtr.lol.domain.championstats.application;

import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsCachePort;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsMetricsPort;
import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsQueryPort;
import com.mmrtr.lol.domain.championstats.domain.TierFilter;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionAverageStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionBootBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionMatchupReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionRuneBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSkillBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionSpellStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStartItemBuildReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionWinRateReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** lol-server {@code ChampionStatsServiceConcurrencyTest} 이전 — 실제 스레드로 single-flight 를 확인한다. */
class ChampionStatsQueryServiceConcurrencyTest {

    private static final int CALLERS = 10;

    private final InMemoryCachePort cachePort = new InMemoryCachePort();
    // 나머지 caller 가 모두 락 대기에 들어설 때까지 BQ 조회를 붙잡아, 대기자가 있는 상태에서 single-flight 를 확인한다
    private final CountingQueryPort queryPort = new CountingQueryPort(() -> cachePort.lockWaiters() >= CALLERS - 1);
    private final ChampionStatsQueryService service = new ChampionStatsQueryService(
            queryPort, cachePort, mock(ChampionStatsMetricsPort.class), Runnable::run);
    private final ExecutorService callerExecutor = Executors.newFixedThreadPool(16);

    @AfterEach
    void tearDown() {
        callerExecutor.shutdownNow();
    }

    @DisplayName("동시에 cache miss 로 진입한 N 요청은 BQ 를 단 1회만 호출한다 (single-flight)")
    @Test
    void concurrentCacheMiss_invokesBigQueryOnce() throws InterruptedException {
        // given
        // by-position 캐시 hit 으로 nested single-flight 단락
        cachePort.seedByPosition("16.1", "KR", "EMERALD", List.of());

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(CALLERS);

        // when
        for (int i = 0; i < CALLERS; i++) {
            callerExecutor.submit(() -> {
                try {
                    startGate.await();
                    service.getChampionStats(13, "kr", "16.1", "EMERALD");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishGate.countDown();
                }
            });
        }
        startGate.countDown();
        boolean done = finishGate.await(10, TimeUnit.SECONDS);

        // then
        assertThat(done).isTrue();
        assertThat(queryPort.sawAllWaiters).as("조회 중에 나머지 caller 가 모두 락을 기다리고 있었다").isTrue();
        assertThat(queryPort.winRatesCalls.get())
                .as("동시 진입 N 요청에 대해 getChampionWinRates 는 단일 호출자만 실행해야 한다")
                .isEqualTo(1);
    }

    private static class CountingQueryPort implements ChampionStatsQueryPort {
        private static final long HOLD_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2);

        final AtomicInteger winRatesCalls = new AtomicInteger();
        volatile boolean sawAllWaiters;
        private final BooleanSupplier allWaiting;

        CountingQueryPort(BooleanSupplier allWaiting) {
            this.allWaiting = allWaiting;
        }

        @Override
        public List<ChampionWinRateReadModel> getChampionWinRates(
                int championId, String patch, String platformId, TierFilter tierFilter) {
            winRatesCalls.incrementAndGet();
            long deadline = System.nanoTime() + HOLD_TIMEOUT_NANOS;
            while (!allWaiting.getAsBoolean() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            sawAllWaiters = allWaiting.getAsBoolean();
            return List.of();
        }

        @Override
        public List<ChampionAverageStatsReadModel> getChampionAverageStats(
                int championId, String patch, String platformId, TierFilter tierFilter) {
            return List.of();
        }

        @Override
        public List<ChampionMatchupReadModel> getChampionMatchups(
                int championId, String patch, String platformId, TierFilter tierFilter, String position) {
            return List.of();
        }

        @Override
        public List<ChampionRuneBuildReadModel> getChampionRuneBuilds(
                int championId, String patch, String platformId, TierFilter tierFilter, String position) {
            return List.of();
        }

        @Override
        public List<ChampionSpellStatsReadModel> getChampionSpellStats(
                int championId, String patch, String platformId, TierFilter tierFilter, String position) {
            return List.of();
        }

        @Override
        public List<ChampionSkillBuildReadModel> getChampionSkillBuilds(
                int championId, String patch, String platformId, TierFilter tierFilter, String position) {
            return List.of();
        }

        @Override
        public List<ChampionStartItemBuildReadModel> getChampionStartItemBuilds(
                int championId, String patch, String platformId, TierFilter tierFilter, String position) {
            return List.of();
        }

        @Override
        public List<ChampionBootBuildReadModel> getChampionBootBuilds(
                int championId, String patch, String platformId, TierFilter tierFilter, String position) {
            return List.of();
        }

        @Override
        public List<ChampionItemBuildReadModel> getChampionItemBuilds(
                int championId, String patch, String platformId, TierFilter tierFilter, String position) {
            return List.of();
        }

        @Override
        public Map<String, List<ChampionRateReadModel>> getChampionStatsByPosition(
                String patch, String platformId, TierFilter tierFilter) {
            return Map.of();
        }
    }

    private static class InMemoryCachePort implements ChampionStatsCachePort {
        private final ConcurrentHashMap<String, ChampionStatsReadModel> detailCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, List<PositionChampionStatsReadModel>> positionsCache =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

        void seedByPosition(String patch, String platformId, String tierDisplay,
                            List<PositionChampionStatsReadModel> value) {
            positionsCache.put(positionsKey(patch, platformId, tierDisplay), value);
        }

        /** 락을 기다리는 스레드 수 (시간 제한 tryLock 도 대기열에 선다). */
        int lockWaiters() {
            return locks.values().stream().mapToInt(ReentrantLock::getQueueLength).sum();
        }

        @Override
        public Optional<ChampionStatsReadModel> findChampionStats(
                int championId, String patch, String platformId, String tierDisplay) {
            return Optional.ofNullable(detailCache.get(detailKey(championId, patch, platformId, tierDisplay)));
        }

        @Override
        public void saveChampionStats(int championId, String patch, String platformId,
                                      String tierDisplay, ChampionStatsReadModel stats) {
            detailCache.put(detailKey(championId, patch, platformId, tierDisplay), stats);
        }

        @Override
        public Optional<List<PositionChampionStatsReadModel>> findChampionStatsByPosition(
                String patch, String platformId, String tierDisplay) {
            return Optional.ofNullable(positionsCache.get(positionsKey(patch, platformId, tierDisplay)));
        }

        @Override
        public void saveChampionStatsByPosition(String patch, String platformId, String tierDisplay,
                                                List<PositionChampionStatsReadModel> stats) {
            positionsCache.put(positionsKey(patch, platformId, tierDisplay), stats);
        }

        @Override
        public boolean tryLockDetail(int championId, String patch, String platformId, String tierDisplay) {
            return tryLock("detail:" + detailKey(championId, patch, platformId, tierDisplay));
        }

        @Override
        public void unlockDetail(int championId, String patch, String platformId, String tierDisplay) {
            unlock("detail:" + detailKey(championId, patch, platformId, tierDisplay));
        }

        @Override
        public boolean tryLockByPosition(String patch, String platformId, String tierDisplay) {
            return tryLock("positions:" + positionsKey(patch, platformId, tierDisplay));
        }

        @Override
        public void unlockByPosition(String patch, String platformId, String tierDisplay) {
            unlock("positions:" + positionsKey(patch, platformId, tierDisplay));
        }

        private boolean tryLock(String key) {
            ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
            try {
                return lock.tryLock(3L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private void unlock(String key) {
            ReentrantLock lock = locks.get(key);
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        private String detailKey(int championId, String patch, String platformId, String tierDisplay) {
            return platformId + ":" + championId + ":" + patch + ":" + tierDisplay;
        }

        private String positionsKey(String patch, String platformId, String tierDisplay) {
            return platformId + ":" + patch + ":" + tierDisplay;
        }
    }
}
