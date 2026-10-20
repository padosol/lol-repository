package com.mmrtr.lol.infra.redis.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummonerLookupLockRedisAdapterTest {

    private RLock lock;
    private SummonerLookupLockRedisAdapter adapter;

    @BeforeEach
    void setUp() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(redissonClient.getLock("summoner:lock:lookup:KR:hideonbush#kr1")).thenReturn(lock);
        adapter = new SummonerLookupLockRedisAdapter(redissonClient);
    }

    @DisplayName("락을 잡으면 작업 결과를 돌려주고 락을 푼다 — lease 없이 5초 대기 (watchdog 연장)")
    @Test
    void executeWithLock_acquired_runsActionAndUnlocks() throws InterruptedException {
        // given
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        // when
        Optional<String> result = adapter.executeWithLock("KR:hideonbush#kr1", () -> "saved");

        // then
        assertThat(result).contains("saved");
        verify(lock).unlock();
    }

    @DisplayName("대기 시간 안에 못 잡으면 작업을 실행하지 않고 empty")
    @Test
    @SuppressWarnings("unchecked")
    void executeWithLock_notAcquired_skipsAction() throws InterruptedException {
        // given
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(false);
        Supplier<String> action = mock(Supplier.class);

        // when
        Optional<String> result = adapter.executeWithLock("KR:hideonbush#kr1", action);

        // then
        assertThat(result).isEmpty();
        verify(action, never()).get();
        verify(lock, never()).unlock();
    }

    @DisplayName("작업이 실패해도 락을 푼다")
    @Test
    void executeWithLock_actionThrows_unlocks() throws InterruptedException {
        // given
        when(lock.tryLock(5, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> adapter.executeWithLock("KR:hideonbush#kr1", () -> {
            throw new IllegalStateException("riot down");
        })).isInstanceOf(IllegalStateException.class);
        verify(lock).unlock();
    }
}
