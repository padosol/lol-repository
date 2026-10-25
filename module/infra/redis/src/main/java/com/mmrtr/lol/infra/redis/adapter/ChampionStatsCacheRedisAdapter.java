package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsCachePort;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 챔피언 통계 캐시와 single-flight 락 (championstats 컨텍스트). TTL·락 시간은 lol-server 와 같다.
 * lol-server 의 {@code champion-stats:*} 와 키를 공유하지 않는다 — 값 형식이 다르고, 락을 공유하면
 * 서로 다른 캐시가 채워지길 기다리다 fallback 으로 빠진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChampionStatsCacheRedisAdapter implements ChampionStatsCachePort {

    static final String DETAIL_KEY_PREFIX = "championstats:v1:detail:";
    static final String POSITIONS_KEY_PREFIX = "championstats:v1:positions:";
    static final String DETAIL_LOCK_PREFIX = "championstats:lock:detail:";
    static final String POSITIONS_LOCK_PREFIX = "championstats:lock:positions:";
    static final Duration TTL = Duration.ofHours(6);
    static final long LOCK_WAIT_SECONDS = 3L;
    // BigQuery 가 멈춰도 락이 계속 잡혀 있지 않게 lease 를 둔다 (넘기면 다른 요청이 한 번 더 조회할 뿐이다)
    static final long LOCK_LEASE_SECONDS = 30L;

    private final JsonValueCache cache;
    private final RedissonClient redissonClient;

    @Override
    public Optional<ChampionStatsReadModel> findChampionStats(
            int championId, String patch, String platformId, String tierDisplay) {
        return cache.get(detailKey(championId, patch, platformId, tierDisplay), ChampionStatsReadModel.class);
    }

    @Override
    public void saveChampionStats(
            int championId, String patch, String platformId, String tierDisplay, ChampionStatsReadModel stats) {
        cache.set(detailKey(championId, patch, platformId, tierDisplay), stats, TTL);
    }

    @Override
    public Optional<List<PositionChampionStatsReadModel>> findChampionStatsByPosition(
            String patch, String platformId, String tierDisplay) {
        return cache.get(positionsKey(patch, platformId, tierDisplay), PositionsView.class)
                .map(PositionsView::positions);
    }

    @Override
    public void saveChampionStatsByPosition(
            String patch, String platformId, String tierDisplay, List<PositionChampionStatsReadModel> stats) {
        cache.set(positionsKey(patch, platformId, tierDisplay), new PositionsView(stats), TTL);
    }

    @Override
    public boolean tryLockDetail(int championId, String patch, String platformId, String tierDisplay) {
        return tryLock(DETAIL_LOCK_PREFIX + detailSuffix(championId, patch, platformId, tierDisplay));
    }

    @Override
    public void unlockDetail(int championId, String patch, String platformId, String tierDisplay) {
        unlock(DETAIL_LOCK_PREFIX + detailSuffix(championId, patch, platformId, tierDisplay));
    }

    @Override
    public boolean tryLockByPosition(String patch, String platformId, String tierDisplay) {
        return tryLock(POSITIONS_LOCK_PREFIX + positionsSuffix(patch, platformId, tierDisplay));
    }

    @Override
    public void unlockByPosition(String patch, String platformId, String tierDisplay) {
        unlock(POSITIONS_LOCK_PREFIX + positionsSuffix(patch, platformId, tierDisplay));
    }

    private boolean tryLock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("champion-stats lock interrupted - key: {}", lockKey);
            return false;
        }
    }

    private void unlock(String lockKey) {
        try {
            RLock lock = redissonClient.getLock(lockKey);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException e) {
            log.warn("champion-stats lock release failed - key: {}, cause: {}", lockKey, e.getMessage());
        }
    }

    private static String detailKey(int championId, String patch, String platformId, String tierDisplay) {
        return DETAIL_KEY_PREFIX + detailSuffix(championId, patch, platformId, tierDisplay);
    }

    private static String positionsKey(String patch, String platformId, String tierDisplay) {
        return POSITIONS_KEY_PREFIX + positionsSuffix(patch, platformId, tierDisplay);
    }

    private static String detailSuffix(int championId, String patch, String platformId, String tierDisplay) {
        return platformId + ":" + championId + ":" + patch + ":" + tierDisplay;
    }

    private static String positionsSuffix(String patch, String platformId, String tierDisplay) {
        return platformId + ":" + patch + ":" + tierDisplay;
    }

    /** 빈 목록과 미스를 가르려고 목록을 객체로 감싼다. */
    record PositionsView(List<PositionChampionStatsReadModel> positions) {
    }
}
