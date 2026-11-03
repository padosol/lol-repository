package com.mmrtr.lol.infra.redis.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** 클릭 쿨다운 키·값·TTL. 진행 표시는 갱신 레코드가 맡는다. */
class SummonerRenewalStateRedisAdapterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SummonerRenewalStateRedisAdapter adapter = new SummonerRenewalStateRedisAdapter(redisTemplate);

    SummonerRenewalStateRedisAdapterTest() {
        given(redisTemplate.opsForValue()).willReturn(values);
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
