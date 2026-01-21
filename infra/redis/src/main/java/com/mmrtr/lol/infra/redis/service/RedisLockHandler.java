package com.mmrtr.lol.infra.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RedisLockHandler {
    private static final String UNLOCK_LUA =
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public boolean acquireLock(String puuid, Duration duration) {
        String lockKey = "summoner:lock:" + puuid;

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(
                        lockKey,
                        puuid,
                        duration);

        if (Boolean.TRUE.equals(success)) {
            return true;
        }

        return false;
    }

    public boolean releaseLock(String puuid) {
        String lockKey = "summoner:lock:" + puuid;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(lockKey),
                puuid
        );

        return result != null && result == 1L;
    }

    public void deleteSummonerRenewal(String puuid) {
        stringRedisTemplate.delete(puuid);
    }
}
