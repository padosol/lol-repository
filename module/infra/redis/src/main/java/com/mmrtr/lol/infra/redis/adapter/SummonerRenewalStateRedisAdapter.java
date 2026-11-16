package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.summoner.application.port.SummonerRenewalStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 갱신 진행 표시·클릭 쿨다운. 키·값·TTL 이 lol-server {@code SummonerCacheAdapter} 와 같다 (값은 puuid 문자열 —
 * 형식이 같아 두 서비스가 키를 공유한다). 진행 표시는 갱신 컨슈머가 끝날 때 {@code RedisLockHandler} 가 지운다.
 */
@Component
@RequiredArgsConstructor
public class SummonerRenewalStateRedisAdapter implements SummonerRenewalStatePort {

    static final String RENEWAL_KEY_PREFIX = "summoner:renewal:";
    static final String CLICK_COOLDOWN_KEY_PREFIX = "summoner:click-cooldown:";
    static final Duration RENEWAL_TTL = Duration.ofMinutes(2);
    static final Duration CLICK_COOLDOWN_TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isRenewing(String puuid) {
        return StringUtils.hasText(redisTemplate.opsForValue().get(RENEWAL_KEY_PREFIX + puuid));
    }

    /** lol-server 는 확인과 설정을 따로 해 동시 클릭이 둘 다 통과할 수 있었다. SET NX 로 하나만 통과시킨다. */
    @Override
    public boolean tryStartClickCooldown(String puuid) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(CLICK_COOLDOWN_KEY_PREFIX + puuid, puuid, CLICK_COOLDOWN_TTL));
    }

    @Override
    public void markRenewing(String puuid) {
        redisTemplate.opsForValue().set(RENEWAL_KEY_PREFIX + puuid, puuid, RENEWAL_TTL);
    }
}
