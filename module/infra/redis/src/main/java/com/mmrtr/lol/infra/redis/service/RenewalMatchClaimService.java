package com.mmrtr.lol.infra.redis.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 사용자 갱신 전용 매치 선점. 백그라운드 적재의 {@code match:pending}·{@code match:processing}
 * 과는 별개 키를 쓴다. 사용자 쪽 판정의 정확성에 쓰이므로 Redis 오류는 fail-open 하지 않고 그대로 올린다.
 * 선점은 풀지 않고 TTL(1분)로만 사라진다. 그 뒤는 DB 확인이 1차로 거른다.
 */
@Service
public class RenewalMatchClaimService {

    static final Duration CLAIM_TTL = Duration.ofMinutes(1);

    private static final String CLAIM_PREFIX = "renewal:match:claim:";

    private final StringRedisTemplate redis;

    public RenewalMatchClaimService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 이미 같은 renewalId 가 선점했으면(재전달) true. */
    public boolean claim(String matchId, String renewalId) {
        String key = CLAIM_PREFIX + matchId;
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, renewalId, CLAIM_TTL))) {
            return true;
        }
        return renewalId.equals(redis.opsForValue().get(key));
    }

    /** 다른 갱신이 선점한 매치. 아무도 쥐지 않았거나 renewalId 가 쥔(재전달) 매치는 빠진다. */
    public Set<String> findClaimedByOthers(Collection<String> matchIds, String renewalId) {
        Set<String> result = new HashSet<>();
        if (matchIds.isEmpty()) {
            return result;
        }
        List<String> ids = new ArrayList<>(matchIds);
        List<String> keys = ids.stream().map(id -> CLAIM_PREFIX + id).toList();
        List<String> values = redis.opsForValue().multiGet(keys);
        for (int i = 0; values != null && i < ids.size(); i++) {
            String owner = values.get(i);
            if (owner != null && !owner.equals(renewalId)) {
                result.add(ids.get(i));
            }
        }
        return result;
    }
}
