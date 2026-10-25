package com.mmrtr.lol.infra.redis.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** lol-server {@code SummonerCacheAdapter} 와 같은 키·값·TTL 인지 — 두 서비스가 키를 공유한다. */
class SummonerRenewalStateRedisAdapterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SummonerRenewalStateRedisAdapter adapter = new SummonerRenewalStateRedisAdapter(redisTemplate);

    SummonerRenewalStateRedisAdapterTest() {
        given(redisTemplate.opsForValue()).willReturn(values);
    }

    @DisplayName("진행 표시는 summoner:renewal:{puuid} = puuid, 2분")
    @Test
    void markRenewing_puuid_setsLolServerKey() {
        // when
        adapter.markRenewing("p1");
        given(values.get("summoner:renewal:p1")).willReturn("p1");

        // then
        verify(values).set("summoner:renewal:p1", "p1", Duration.ofMinutes(2));
        assertThat(adapter.isRenewing("p1")).isTrue();
        assertThat(adapter.isRenewing("p2")).isFalse();
    }

    @DisplayName("클릭 쿨다운은 summoner:click-cooldown:{puuid} 에 SET NX 10초 — 이미 있으면 false")
    @Test
    void tryStartClickCooldown_setIfAbsent_returnsWhetherSet() {
        // given
        given(values.setIfAbsent("summoner:click-cooldown:p1", "p1", Duration.ofSeconds(10))).willReturn(true);
        given(values.setIfAbsent("summoner:click-cooldown:p2", "p2", Duration.ofSeconds(10))).willReturn(false);

        // when & then
        assertThat(adapter.tryStartClickCooldown("p1")).isTrue();
        assertThat(adapter.tryStartClickCooldown("p2")).isFalse();
    }
}
