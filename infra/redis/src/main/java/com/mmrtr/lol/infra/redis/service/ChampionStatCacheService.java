package com.mmrtr.lol.infra.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionStatCacheService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final String KEY_PREFIX = "champion_stat:";

    public String buildCacheKey(String type, int championId, String position, int season,
                                String tierGroup, String platform, int queueId, String patch) {
        return KEY_PREFIX + type + ":" + championId + ":" + position + ":" + season + ":"
                + tierGroup + ":" + platform + ":" + queueId + ":" + patch;
    }

    public Optional<String> get(String cacheKey) {
        String value = stringRedisTemplate.opsForValue().get(cacheKey);
        return Optional.ofNullable(value);
    }

    public void put(String cacheKey, String jsonValue) {
        stringRedisTemplate.opsForValue().set(cacheKey, jsonValue, CACHE_TTL);
    }

    public void evictAll() {
        Set<String> keys = stringRedisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("챔피언 통계 캐시 무효화 완료 - {} 건", keys.size());
        }
    }
}
