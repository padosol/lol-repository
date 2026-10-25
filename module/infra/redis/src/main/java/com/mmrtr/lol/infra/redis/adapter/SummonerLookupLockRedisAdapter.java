package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.summoner.application.port.SummonerLookupLockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Riot ID 조회 락. 대기 5초는 lol-server 와 같다. lease 는 주지 않아 Redisson watchdog 이 작업이 끝날 때까지 연장한다
 * (lol-server 의 10초 lease 는 Riot rate limit 대기로 조회가 길어지면 먼저 풀려 중복 호출을 막지 못한다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummonerLookupLockRedisAdapter implements SummonerLookupLockPort {

    /** 갱신 컨슈머의 {@code summoner:lock:{puuid}} 와 겹치지 않는다. */
    static final String KEY_PREFIX = "summoner:lock:lookup:";
    static final long WAIT_SECONDS = 5;

    private final RedissonClient redissonClient;

    @Override
    public <T> Optional<T> executeWithLock(String key, Supplier<T> action) {
        RLock lock = redissonClient.getLock(KEY_PREFIX + key);
        try {
            if (!lock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS)) {
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // key 는 사용자 입력(Riot ID)이라 로그에 남기지 않는다
            log.warn("소환사 조회 락 대기 중 인터럽트");
            return Optional.empty();
        }
        try {
            return Optional.of(action.get());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
