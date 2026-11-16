package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.summoner.application.port.SummonerRenewalStatePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 클릭 쿨다운. 갱신 진행 여부는 갱신 레코드({@code RenewalRecordRedisAdapter})가 맡는다. */
@Component
@RequiredArgsConstructor
public class SummonerRenewalStateRedisAdapter implements SummonerRenewalStatePort {

    static final String CLICK_COOLDOWN_KEY_PREFIX = "summoner:click-cooldown:";
    static final Duration CLICK_COOLDOWN_TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate redisTemplate;

    /** 확인과 설정을 SET NX 로 한 번에 해 동시 클릭 중 하나만 통과시킨다. */
    @Override
    public boolean tryStartClickCooldown(String puuid) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(CLICK_COOLDOWN_KEY_PREFIX + puuid, puuid, CLICK_COOLDOWN_TTL));
    }
}
