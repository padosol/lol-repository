package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.match.application.port.MatchCacheEvictPort;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * lol-server 매치 리스트 캐시(`match:list:v1:{puuid}:{season}:{queueId}:{pageNo}`)를
 * Redisson SCAN+DEL 패턴으로 제거하는 adapter.
 *
 * <p>실패해도 호출자(저장 파이프라인) 에 예외를 전파하지 않는 best-effort 동작.
 */
@Slf4j
@Component
public class MatchCacheEvictAdapter implements MatchCacheEvictPort {

    private static final String MATCH_LIST_PATTERN_PREFIX = "match:list:v1:";

    private final RedissonClient redissonClient;
    private final MeterRegistry meterRegistry;

    public MatchCacheEvictAdapter(RedissonClient redissonClient, MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void evictByPuuids(Set<String> puuids) {
        if (puuids == null || puuids.isEmpty()) {
            return;
        }

        long deleted = 0L;
        try {
            RKeys keys = redissonClient.getKeys();
            for (String puuid : puuids) {
                String pattern = MATCH_LIST_PATTERN_PREFIX + puuid + ":*";
                deleted += keys.deleteByPattern(pattern);
            }
            log.info("cache.match.evict puuid_count={} keys_deleted={}", puuids.size(), deleted);
            meterRegistry.counter("cache.match.evict", "result", "success").increment();
        } catch (Exception e) {
            log.warn("cache.match.evict failed puuid_count={} cause={}", puuids.size(), e.getMessage());
            meterRegistry.counter("cache.match.evict", "result", "failure").increment();
        }
    }
}
