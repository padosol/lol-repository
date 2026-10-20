package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmrtr.lol.domain.match.application.port.MatchCacheReadPort;
import com.mmrtr.lol.domain.match.readmodel.GameReadModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link MatchCacheWriteAdapter} 가 쓴 {@code match:ids:v1:{puuid}} ZSET 과 {@code match:v1:{matchId}} JSON 을 읽는다.
 * 값은 Redisson StringCodec 으로 쓴 따옴표 없는 문자열·JSON 이라 {@link StringRedisTemplate} 으로 읽는다.
 * 단건 JSON 은 lol-server {@code GameReadModel} 형태({@code MatchCacheView})라 {@link GameReadModel} 로 그대로 읽힌다.
 */
@Slf4j
@Component
public class MatchCacheReadAdapter implements MatchCacheReadPort {

    private static final String MATCH_KEY_PREFIX = "match:v1:";
    private static final String MATCH_IDS_KEY_PREFIX = "match:ids:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MatchCacheReadAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        // 쓰기 측 view 에만 있는 필드가 생겨도 읽기를 깨뜨리지 않는다
        this.objectMapper = objectMapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public List<String> findRecentMatchIds(String puuid) {
        try {
            Set<String> matchIds = redisTemplate.opsForZSet().reverseRange(MATCH_IDS_KEY_PREFIX + puuid, 0, -1);
            return matchIds == null ? List.of() : new ArrayList<>(matchIds);
        } catch (RuntimeException e) {
            log.warn("cache.match.ids read failed cause={}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Map<String, GameReadModel> findGames(Collection<String> matchIds) {
        if (matchIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = new ArrayList<>(matchIds);
        List<String> values;
        try {
            values = redisTemplate.opsForValue().multiGet(ids.stream().map(id -> MATCH_KEY_PREFIX + id).toList());
        } catch (RuntimeException e) {
            log.warn("cache.match read failed count={} cause={}", ids.size(), e.getMessage());
            return Map.of();
        }
        if (values == null) {
            return Map.of();
        }

        Map<String, GameReadModel> games = new HashMap<>();
        for (int i = 0; i < Math.min(ids.size(), values.size()); i++) {
            String json = values.get(i);
            if (json == null) {
                continue;
            }
            try {
                games.put(ids.get(i), objectMapper.readValue(json, GameReadModel.class));
            } catch (Exception e) {
                log.warn("cache.match deserialize failed cause={}", e.getMessage());
            }
        }
        return games;
    }
}
