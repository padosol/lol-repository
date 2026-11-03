package com.mmrtr.lol.infra.redis.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 사용자 갱신 전용 매치 선점·실패 표시. 백그라운드 적재의 {@code match:pending}·{@code match:processing}
 * 과는 별개 키를 쓴다. 사용자 쪽 판정의 정확성에 쓰이므로 Redis 오류는 fail-open 하지 않고 그대로 올린다.
 */
@Service
public class RenewalMatchClaimService {

    static final Duration CLAIM_TTL = Duration.ofMinutes(5);
    static final Duration FAILED_TTL = Duration.ofMinutes(5);
    static final Duration FAILED_COUNT_TTL = Duration.ofDays(1);
    static final int EXCLUDE_THRESHOLD = 3;

    private static final String CLAIM_PREFIX = "renewal:match:claim:";
    private static final String FAILED_PREFIX = "renewal:match:failed:";
    private static final String FAILED_COUNT_PREFIX = "renewal:match:failed-count:";

    private static final DefaultRedisScript<Long> RELEASE = script("renewal/claim-release.lua");
    private static final DefaultRedisScript<Long> MARK_FAILED = script("renewal/claim-mark-failed.lua");

    private final StringRedisTemplate redis;

    public RenewalMatchClaimService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static DefaultRedisScript<Long> script(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }

    /** 이미 같은 renewalId 가 선점했으면(재전달) true. */
    public boolean claim(String matchId, String renewalId) {
        String key = CLAIM_PREFIX + matchId;
        if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, renewalId, CLAIM_TTL))) {
            return true;
        }
        return renewalId.equals(redis.opsForValue().get(key));
    }

    public void release(String matchId, String renewalId) {
        redis.execute(RELEASE, List.of(CLAIM_PREFIX + matchId), renewalId);
    }

    /** 실패 표시(값 = 지금 ms, TTL 5분)와 실패 횟수(+1, TTL 1일)를 Lua 하나로 함께 쓴다. TTL 없는 횟수가 남지 않는다. */
    public void markFailed(String matchId) {
        redis.execute(MARK_FAILED, List.of(FAILED_PREFIX + matchId, FAILED_COUNT_PREFIX + matchId),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(FAILED_TTL.toSeconds()),
                String.valueOf(FAILED_COUNT_TTL.toSeconds()));
    }

    /** 실패 표시가 있는 매치만 담는다. 값은 표시한 시각(ms). */
    public Map<String, Long> findFailedAt(Collection<String> matchIds) {
        Map<String, Long> result = new HashMap<>();
        if (matchIds.isEmpty()) {
            return result;
        }
        List<String> ids = new ArrayList<>(matchIds);
        List<String> values = redis.opsForValue().multiGet(prefixed(FAILED_PREFIX, ids));
        for (int i = 0; values != null && i < ids.size(); i++) {
            if (values.get(i) != null) {
                result.put(ids.get(i), Long.parseLong(values.get(i)));
            }
        }
        return result;
    }

    /** 실패 횟수가 임계(3) 이상이라 대상에서 뺄 매치. */
    public Set<String> findExcluded(Collection<String> matchIds) {
        Set<String> result = new HashSet<>();
        if (matchIds.isEmpty()) {
            return result;
        }
        List<String> ids = new ArrayList<>(matchIds);
        List<String> values = redis.opsForValue().multiGet(prefixed(FAILED_COUNT_PREFIX, ids));
        for (int i = 0; values != null && i < ids.size(); i++) {
            if (values.get(i) != null && Long.parseLong(values.get(i)) >= EXCLUDE_THRESHOLD) {
                result.add(ids.get(i));
            }
        }
        return result;
    }

    private static List<String> prefixed(String prefix, List<String> ids) {
        return ids.stream().map(id -> prefix + id).toList();
    }
}
