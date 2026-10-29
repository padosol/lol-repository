package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.championstats.application.port.ChampionStatsCachePort;
import com.mmrtr.lol.domain.championstats.readmodel.ChampionStatsReadModel;
import com.mmrtr.lol.domain.championstats.readmodel.PositionChampionStatsReadModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
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
    static final int EVICT_BATCH_SIZE = 500;

    private final JsonValueCache cache;
    private final RedissonClient redissonClient;

    // 과도기 설정: lol-server 가 챔피언 통계를 서빙하는 동안만 그쪽 캐시 키(champion-stats:v*)도 함께 비운다.
    // MP-179 Ingress 전환 후 championstats.cache.legacy-evict-patterns 를 비운다. (생성자 주입 대상 아님)
    @Value("${championstats.cache.legacy-evict-patterns:champion-stats:v[0-9]*}")
    private List<String> legacyEvictPatterns;

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

    @Override
    public long evictAll() {
        List<String> patterns = new ArrayList<>(List.of(DETAIL_KEY_PREFIX + "*", POSITIONS_KEY_PREFIX + "*"));
        if (legacyEvictPatterns != null) {
            patterns.addAll(legacyEvictPatterns);
        }
        long deleted = 0;
        try {
            RKeys keys = redissonClient.getKeys();
            for (String pattern : patterns) {
                deleted += unlinkByPattern(keys, pattern);
            }
        } catch (RuntimeException e) {
            log.warn("champion-stats cache evict failed - deleted so far: {}, cause: {}", deleted, e.getMessage());
            return deleted;
        }
        return deleted;
    }

    // SCAN 으로 훑어 일정 개수씩 UNLINK 한다 (KEYS 는 Redis 를 막으므로 쓰지 않는다)
    private long unlinkByPattern(RKeys keys, String pattern) {
        long deleted = 0;
        List<String> batch = new ArrayList<>(EVICT_BATCH_SIZE);
        for (String key : keys.getKeys(KeysScanOptions.defaults().pattern(pattern).chunkSize(EVICT_BATCH_SIZE))) {
            batch.add(key);
            if (batch.size() >= EVICT_BATCH_SIZE) {
                deleted += keys.unlink(batch.toArray(new String[0]));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            deleted += keys.unlink(batch.toArray(new String[0]));
        }
        return deleted;
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
