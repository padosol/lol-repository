package com.mmrtr.lol.infra.redis.adapter;

import com.mmrtr.lol.domain.summoner.application.port.RenewalRecordPort;
import com.mmrtr.lol.domain.summoner.domain.RenewalRecord;
import com.mmrtr.lol.domain.summoner.domain.RenewalStatus;
import com.mmrtr.lol.domain.summoner.domain.RenewalStep;
import com.mmrtr.lol.domain.summoner.domain.RenewalTransition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 갱신 레코드 Redis 어댑터. 상태 전이는 Lua 로 원자적으로 하고, 시각은 Redis TIME 으로 비교한다.
 * 키는 lol-server 의 {@code summoner:renewal:*} 스캔 패턴과 겹치지 않게 {@code renewal-v2} 를 쓴다.
 */
@Component
public class RenewalRecordRedisAdapter implements RenewalRecordPort {

    static final Duration DEADLINE = Duration.ofSeconds(5);
    static final Duration PROGRESS_TTL = Duration.ofMinutes(5);
    static final Duration DONE_TTL = Duration.ofSeconds(30);
    static final Duration DEADLINE_FAILED_TTL = Duration.ofMinutes(5);

    private static final String KEY_PREFIX = "summoner:renewal-v2:";
    private static final String ACTIVE_KEY = KEY_PREFIX + "active";
    private static final RenewalTransition[] TRANSITIONS = RenewalTransition.values();

    private static final DefaultRedisScript<Long> OPEN = script("renewal/renewal-open.lua");
    private static final DefaultRedisScript<Long> MARK = script("renewal/renewal-mark.lua");
    private static final DefaultRedisScript<Long> FAIL = script("renewal/renewal-fail.lua");
    private static final DefaultRedisScript<Long> UPDATE = script("renewal/renewal-update.lua");

    private final StringRedisTemplate redis;

    public RenewalRecordRedisAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new org.springframework.core.io.ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    private static String recordKey(String puuid) {
        return KEY_PREFIX + puuid;
    }

    @Override
    public long open(String puuid, String renewalId, String platform) {
        Long acceptedAt = redis.execute(OPEN,
                List.of(recordKey(puuid), ACTIVE_KEY),
                renewalId, platform, puuid,
                String.valueOf(DEADLINE.toMillis()), String.valueOf(PROGRESS_TTL.toSeconds()));
        return acceptedAt == null ? 0L : acceptedAt;
    }

    @Override
    public Optional<RenewalRecord> find(String puuid) {
        Map<Object, Object> hash = redis.opsForHash().entries(recordKey(puuid));
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        String revision = (String) hash.get("riotRevisionDate");
        return Optional.of(new RenewalRecord(
                (String) hash.get("renewalId"),
                RenewalStatus.valueOf((String) hash.get("status")),
                (String) hash.get("reason"),
                Integer.parseInt((String) hash.getOrDefault("steps", "0")),
                parseTargets((String) hash.get("targets")),
                Long.parseLong((String) hash.get("acceptedAt")),
                Long.parseLong((String) hash.get("deadlineAt")),
                revision == null ? null : Long.valueOf(revision),
                (String) hash.get("platform")));
    }

    @Override
    public RenewalTransition markSteps(String puuid, String renewalId, Set<RenewalStep> steps) {
        int bits = 0;
        for (RenewalStep step : steps) {
            bits |= step.bit();
        }
        Long result = redis.execute(MARK,
                List.of(recordKey(puuid), ACTIVE_KEY),
                renewalId, String.valueOf(bits), puuid,
                String.valueOf(DONE_TTL.toSeconds()), String.valueOf(DEADLINE_FAILED_TTL.toSeconds()));
        return toTransition(result);
    }

    @Override
    public RenewalTransition fail(String puuid, String renewalId, String reason) {
        return runFail(puuid, renewalId, reason, "fail");
    }

    @Override
    public RenewalTransition failIfOverdue(String puuid, String renewalId) {
        return runFail(puuid, renewalId, DEADLINE_REASON, "overdue");
    }

    private RenewalTransition runFail(String puuid, String renewalId, String reason, String mode) {
        Long result = redis.execute(FAIL,
                List.of(recordKey(puuid), ACTIVE_KEY),
                renewalId, reason, puuid, mode,
                String.valueOf(DONE_TTL.toSeconds()), String.valueOf(DEADLINE_FAILED_TTL.toSeconds()),
                DEADLINE_REASON);
        return toTransition(result);
    }

    @Override
    public void saveRiotRevisionDate(String puuid, String renewalId, long revisionDateMillis) {
        redis.execute(UPDATE, List.of(recordKey(puuid)),
                renewalId, "riotRevisionDate", String.valueOf(revisionDateMillis));
    }

    @Override
    public void recordTargets(String puuid, String renewalId, Collection<String> matchIds) {
        redis.execute(UPDATE, List.of(recordKey(puuid)),
                renewalId, "targets", String.join(",", matchIds));
    }

    /** targets 필드가 없으면 기록 전(null), 빈 문자열이면 대상 없음(빈 Set). */
    private static Set<String> parseTargets(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(java.util.Arrays.asList(value.split(",")));
    }

    @Override
    public List<String> findActive() {
        Set<String> members = redis.opsForZSet().range(ACTIVE_KEY, 0, -1);
        return members == null ? List.of() : new ArrayList<>(members);
    }

    @Override
    public void deactivate(String puuid) {
        redis.opsForZSet().remove(ACTIVE_KEY, puuid);
    }

    private static RenewalTransition toTransition(Long ordinal) {
        if (ordinal == null || ordinal < 0 || ordinal >= TRANSITIONS.length) {
            return RenewalTransition.NONE;
        }
        return TRANSITIONS[ordinal.intValue()];
    }
}
