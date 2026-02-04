package com.mmrtr.lol.infra.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
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

    /**
     * 범용 분산 락 획득
     *
     * @param lockKey   락 키
     * @param lockValue 락 값 (해제 시 검증용)
     * @param duration  락 유지 시간
     * @return 락 획득 성공 여부
     */
    public boolean acquireLock(String lockKey, String lockValue, Duration duration) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, duration);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 범용 분산 락 해제
     *
     * @param lockKey   락 키
     * @param lockValue 락 값 (획득 시 사용한 값과 일치해야 해제됨)
     * @return 락 해제 성공 여부
     */
    public boolean releaseLock(String lockKey, String lockValue) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
        Long result = redisTemplate.execute(
                script,
                Collections.singletonList(lockKey),
                lockValue
        );
        return result != null && result == 1L;
    }

    /**
     * 분산 락 내에서 작업 실행 (재시도 없음)
     * 락 획득 실패 시 즉시 empty 반환
     *
     * @param lockKey  락 키
     * @param duration 락 유지 시간
     * @param action   실행할 작업
     * @return 작업 결과 (락 획득 실패 시 empty)
     */
    public <T> Optional<T> executeWithLock(String lockKey, Duration duration, Supplier<T> action) {
        String lockValue = UUID.randomUUID().toString();

        if (acquireLock(lockKey, lockValue, duration)) {
            try {
                return Optional.ofNullable(action.get());
            } finally {
                releaseLock(lockKey, lockValue);
            }
        }

        return Optional.empty();
    }

    public void deleteSummonerRenewal(String puuid) {
        stringRedisTemplate.delete(puuid);
    }
}
