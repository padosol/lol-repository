package com.mmrtr.lol.infra.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockHandler {

    private final RedissonClient redissonClient;

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
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(0, duration.toMillis(), TimeUnit.MILLISECONDS)) {
                try {
                    return Optional.ofNullable(action.get());
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("락 획득 중 인터럽트 발생: {}", lockKey, e);
        }

        return Optional.empty();
    }
}
