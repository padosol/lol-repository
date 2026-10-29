package com.mmrtr.lol.infra.riot.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.codec.Codec;

/** learnLimits 의 Redis 쓰기가 필드 락 밖에서 일어나고, 실패하면 다음 응답이 다시 쓰는지 본다. */
class RedisRiotRateLimiterLearnTest {

    private static final long NOW = 1_800_000_000_500L;

    private RMap<String, String> limits;
    private RedisRiotRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        limits = mock(RMap.class);
        RedissonClient redisson = mock(RedissonClient.class);
        when(redisson.<String, String>getMap(anyString(), any(Codec.class))).thenReturn(limits);
        limiter = new RedisRiotRateLimiter(redisson, new RiotAPIProperties(), () -> NOW);
    }

    @Test
    @DisplayName("Redis 에 쓰는 동안 같은 필드의 다른 학습이 막히지 않는다")
    void redisWrite_doesNotBlockSameField() {
        AtomicBoolean probed = new AtomicBoolean();
        AtomicBoolean probeFinished = new AtomicBoolean();
        when(limits.fastPut(anyString(), anyString())).thenAnswer(invocation -> {
            if (probed.compareAndSet(false, true)) {
                // 락을 쥔 채 쓰고 있다면 같은 필드를 학습하는 다른 스레드가 끝나지 못한다
                Thread probe = Thread.ofPlatform().start(() -> limiter.learnLimits("asia", "m", "100:10", null));
                probe.join(1_000);
                probeFinished.set(!probe.isAlive());
            }
            return true;
        });

        limiter.learnLimits("asia", "m", "100:10", null);

        assertThat(probeFinished).isTrue();
        verify(limits, times(1)).fastPut("asia|app", "100:10");
    }

    @Test
    @DisplayName("Redis 쓰기가 실패하면 다음 응답이 다시 쓴다")
    void redisWriteFailure_retriedOnNextResponse() {
        when(limits.fastPut(anyString(), anyString()))
                .thenThrow(new RedisConnectionException("down"))
                .thenReturn(true);

        assertThatThrownBy(() -> limiter.learnLimits("asia", "m", "100:10", null))
                .isInstanceOf(RedisConnectionException.class);
        limiter.learnLimits("asia", "m", "100:10", null);

        verify(limits, times(2)).fastPut("asia|app", "100:10");
    }
}
