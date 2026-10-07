package com.mmrtr.lol.infra.redis.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 값을 JSON 문자열로 두는 단순 Redis 캐시. Redis 장애·깨진 값은 캐시 미스로 취급한다 —
 * 조회는 원천(DB/Riot)으로 넘어가고 저장 실패는 로그만 남긴다.
 *
 * <p>lol-server 의 {@code GenericJackson2JsonRedisSerializer}({@code @class} 타입 정보 포함)와 형식이 다르므로
 * 같은 키를 공유하지 않는다 (키는 각 어댑터가 {@code gamedata:} 등 자기 네임스페이스로 둔다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonValueCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("cache read failed - key: {}, cause: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("cache write failed - key: {}, cause: {}", key, e.getMessage());
        }
    }
}
