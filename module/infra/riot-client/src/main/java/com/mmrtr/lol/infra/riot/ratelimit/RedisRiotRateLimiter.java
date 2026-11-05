package com.mmrtr.lol.infra.riot.ratelimit;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Redis ZSET 슬라이딩 창으로 버킷 한도를 지키는 {@link RiotRateLimiter}. 키는 모두 {@code riot:rl:} 아래에 있다.
 *
 * <p>규칙(버킷 × 창)마다 ZSET 하나에 허가된 호출의 eventId 를 허가 시각으로 기록하고, {@link #complete} 가 응답 시각으로
 * 옮긴다. 허가는 Lua 하나가 모든 규칙을 검사한 뒤 전부 통과할 때만 모든 규칙에 기록한다. 한도는 Java 가 버킷별로 알고
 * 인자로 넘긴다.
 *
 * <p>기록은 재시작해도 지우지 않는다. 기동 시 초기화를 하지 않으므로 여러 인스턴스가 같은 예산을 나눠 쓴다.
 */
@Slf4j
@Component
public class RedisRiotRateLimiter implements RiotRateLimiter {

    private static final String PREFIX = "riot:rl:";
    private static final String LIMITS_KEY = PREFIX + "limits";
    /** 같은 한도라도 마지막으로 쓴 지 이만큼 지나면 다시 쓴다. Redis 가 limits 해시를 잃어도 복구된다. */
    private static final long LIMIT_REFRESH_MILLIS = 30_000;
    /** 백필 전용 규칙의 창(초). */
    private static final int BACKGROUND_WINDOW_SECONDS = 1;

    private final RedissonClient redissonClient;
    private final RiotAPIProperties.RateLimit config;
    private final LongSupplier nowMillisOverride;
    private final String appDefault;
    private final LuaScript acquireScript = new LuaScript("ratelimit/riot-acquire.lua");
    private final LuaScript completeScript = new LuaScript("ratelimit/riot-complete.lua");
    private final LuaScript pauseScript = new LuaScript("ratelimit/riot-pause.lua");
    /** 이 인스턴스가 필드별로 마지막에 쓴 한도와 그 시각. */
    private final Map<String, WrittenLimit> lastWrittenLimits = new ConcurrentHashMap<>();
    /** 버킷별로 아는 한도(정규화된 문자열, 배운 값이 없으면 null)와 그 값을 얻은 시각. */
    private final Map<String, CachedLimit> cachedLimits = new ConcurrentHashMap<>();

    @Autowired
    public RedisRiotRateLimiter(RedissonClient redissonClient, RiotAPIProperties properties) {
        this(redissonClient, properties, null);
    }

    /** nowMillisOverride 가 null 이면 Lua 가 Redis TIME 을 시계로 쓴다. */
    RedisRiotRateLimiter(RedissonClient redissonClient, RiotAPIProperties properties, LongSupplier nowMillisOverride) {
        this.redissonClient = redissonClient;
        this.config = properties.getRateLimit();
        this.nowMillisOverride = nowMillisOverride;
        this.appDefault = normalize(config.getAppDefault());
        if (appDefault == null) {
            throw new IllegalArgumentException("riot.rate-limit.app-default 형식이 잘못되었습니다: " + config.getAppDefault());
        }
    }

    @Override
    public long acquire(String eventId, String routing, String method, RiotCallPriority priority) {
        List<Rule> rules = rules(routing, method, priority);
        List<Object> keys = new ArrayList<>(3 + rules.size());
        keys.add(pauseKey(appBucket(routing)));
        keys.add(pauseKey(methodBucket(routing, method)));
        keys.add(PREFIX + "user:" + routing);
        List<Object> args = new ArrayList<>(5 + rules.size() * 3);
        args.add(eventId);
        args.add(nowArg());
        args.add(String.valueOf(priority.level()));
        args.add(String.valueOf(ceilingRatio(priority)));
        args.add(String.valueOf(config.getUserActiveWindow().toMillis()));
        for (Rule rule : rules) {
            keys.add(rule.key());
            args.add(String.valueOf(rule.cap()));
            args.add(String.valueOf(rule.windowMs()));
            args.add(rule.lowered() ? "1" : "0");
        }
        return acquireScript.eval(keys, args.toArray());
    }

    @Override
    public void complete(String eventId, String routing, String method, RiotCallPriority priority) {
        List<Rule> rules = rules(routing, method, priority);
        List<Object> keys = new ArrayList<>(rules.size());
        List<Object> args = new ArrayList<>(2 + rules.size());
        args.add(eventId);
        args.add(nowArg());
        for (Rule rule : rules) {
            keys.add(rule.key());
            args.add(String.valueOf(rule.windowMs()));
        }
        completeScript.eval(keys, args.toArray());
    }

    @Override
    public void learnLimits(String routing, String method, String appLimitHeader, String methodLimitHeader) {
        learn(appBucket(routing), appLimitHeader);
        learn(methodBucket(routing, method), methodLimitHeader);
    }

    @Override
    public void pause(String routing, String methodOrNull, Duration duration) {
        long millis = duration.toMillis();
        if (millis <= 0) {
            return;
        }
        String bucket = methodOrNull == null ? appBucket(routing) : methodBucket(routing, methodOrNull);
        pauseScript.eval(List.of(pauseKey(bucket)), String.valueOf(millis), nowArg());
    }

    /** 규칙 ZSET 키. bucket 은 {@code {routing}|app}, {@code {routing}|m|{method}}, {@code {routing}|bg} 다. */
    static String ruleKey(String bucket, int windowSeconds) {
        return PREFIX + "w:" + bucket + ":" + windowSeconds;
    }

    /**
     * 이 호출이 모두 통과해야 하는 규칙. app 한도(배운 값, 없으면 app-default)와 method 한도(배운 값이 있을 때만)의
     * 창마다 낮춤 규칙 하나씩이고, BACKGROUND 면 백필 전용 1초 규칙을 더한다.
     */
    private List<Rule> rules(String routing, String method, RiotCallPriority priority) {
        List<Rule> rules = new ArrayList<>();
        String app = appBucket(routing);
        String appLimits = knownLimits(app);
        addRules(rules, app, appLimits != null ? appLimits : appDefault);
        String methodBucket = methodBucket(routing, method);
        String methodLimits = knownLimits(methodBucket);
        if (methodLimits != null) {
            addRules(rules, methodBucket, methodLimits);
        }
        int backgroundMax = config.getBackgroundMaxPerSecond();
        if (priority == RiotCallPriority.BACKGROUND && backgroundMax > 0) {
            rules.add(new Rule(ruleKey(backgroundBucket(routing), BACKGROUND_WINDOW_SECONDS), backgroundMax,
                    BACKGROUND_WINDOW_SECONDS * 1000L, false));
        }
        return rules;
    }

    /** 한도의 창마다 상한 max(1, floor(한도 × safetyRatio)) 인 낮춤 규칙을 더한다. */
    private void addRules(List<Rule> rules, String bucket, String limits) {
        for (RateLimitWindow window : RateLimitWindow.parse(limits)) {
            int cap = Math.max(1, (int) Math.floor(window.limit() * config.getSafetyRatio()));
            rules.add(new Rule(ruleKey(bucket, window.windowSeconds()), cap, window.windowSeconds() * 1000L, true));
        }
    }

    /** 유저 활동 중 낮춤 규칙의 상한에 곱할 비율. */
    private double ceilingRatio(RiotCallPriority priority) {
        return switch (priority) {
            case USER -> 1.0;
            case DEFERRED -> config.getDeferredCeilingRatio();
            case BACKGROUND -> config.getBackgroundCeilingRatio();
        };
    }

    /**
     * 버킷의 한도(배운 값이 없으면 null). 캐시가 없거나 얻은 지 limits-refresh 가 지났으면 Redis 에서 다시 읽어
     * 다른 인스턴스가 배운 값을 반영한다. 읽는 사이 {@link #learn} 이 넣은 값은 덮어쓰지 않는다.
     */
    private String knownLimits(String bucket) {
        long now = localNowMillis();
        CachedLimit cached = cachedLimits.get(bucket);
        if (cached != null && now - cached.readAtMillis() <= config.getLimitsRefresh().toMillis()) {
            return cached.limits();
        }
        CachedLimit fresh = new CachedLimit(normalize(limits().get(bucket)), now);
        boolean stored = cached == null
                ? cachedLimits.putIfAbsent(bucket, fresh) == null
                : cachedLimits.replace(bucket, cached, fresh);
        return stored ? fresh.limits() : cachedLimits.get(bucket).limits();
    }

    /**
     * 배운 한도를 캐시에 바로 넣고, 마지막에 쓴 값과 다르거나 쓴 지 {@link #LIMIT_REFRESH_MILLIS} 가 지났으면 HSET 한다.
     * 필드별 판단은 원자적이다. 캐시는 Redis 쓰기 여부와 관계없이 갱신한다.
     *
     * <p>compute 안에서는 판단만 하고 Redis 쓰기는 밖에서 한다. 빈 락을 쥔 채 I/O 하면 가상 스레드가 캐리어에 고정된다.
     * 쓰기가 실패하면 기록을 지워 다음 응답이 다시 쓰게 한다.
     */
    private void learn(String field, String header) {
        String limits = normalize(header);
        if (limits == null) {
            return;
        }
        long now = localNowMillis();
        cachedLimits.put(field, new CachedLimit(limits, now));
        WrittenLimit fresh = new WrittenLimit(limits, now);
        boolean[] changed = new boolean[1];
        WrittenLimit current = lastWrittenLimits.compute(field, (key, written) -> {
            changed[0] = written == null || !written.limits().equals(limits);
            if (!changed[0] && fresh.writtenAtMillis() - written.writtenAtMillis() <= LIMIT_REFRESH_MILLIS) {
                return written;
            }
            return fresh;
        });
        if (current != fresh) {
            return;
        }
        try {
            limits().fastPut(field, limits);
        } catch (RuntimeException e) {
            lastWrittenLimits.remove(field, fresh);
            throw e;
        }
        if (changed[0]) {
            log.info("Riot 한도 갱신 [{}] {}", field, limits);
        }
    }

    private RMap<String, String> limits() {
        return redissonClient.getMap(LIMITS_KEY, StringCodec.INSTANCE);
    }

    private String nowArg() {
        return nowMillisOverride != null ? String.valueOf(nowMillisOverride.getAsLong()) : "";
    }

    private long localNowMillis() {
        return nowMillisOverride != null ? nowMillisOverride.getAsLong() : System.currentTimeMillis();
    }

    private static String appBucket(String routing) {
        return routing + "|app";
    }

    private static String methodBucket(String routing, String method) {
        return routing + "|m|" + method;
    }

    private static String backgroundBucket(String routing) {
        return routing + "|bg";
    }

    private static String pauseKey(String bucket) {
        return PREFIX + "pause:" + bucket;
    }

    /** 헤더를 "limit:W,..." 로 정규화한다. 유효한 창이 없으면 null. Redis 해시와 한도 캐시에 이 형식으로 둔다. */
    private static String normalize(String header) {
        List<RateLimitWindow> windows = RateLimitWindow.parse(header);
        if (windows.isEmpty()) {
            return null;
        }
        return windows.stream()
                .map(w -> w.limit() + ":" + w.windowSeconds())
                .collect(Collectors.joining(","));
    }

    private static boolean isNoScript(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("NOSCRIPT")) {
                return true;
            }
        }
        return false;
    }

    private record WrittenLimit(String limits, long writtenAtMillis) {
    }

    /** limits 는 정규화된 한도 문자열, 배운 값이 없으면 null. */
    private record CachedLimit(String limits, long readAtMillis) {
    }

    /** lowered = 유저 활동 중 우선순위별 비율로 상한을 낮추는 규칙(app·method). 백필 전용 규칙은 false. */
    private record Rule(String key, int cap, long windowMs, boolean lowered) {
    }

    /** 처음 쓸 때 scriptLoad 하고 evalSha 로 부른다. Redis 재시작으로 캐시가 비면(NOSCRIPT) 다시 올려 한 번 더 부른다. */
    private final class LuaScript {

        private final String source;
        private volatile String sha;

        LuaScript(String path) {
            try (InputStream in = RedisRiotRateLimiter.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("Lua 스크립트가 없습니다: " + path);
                }
                this.source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Lua 스크립트를 읽지 못했습니다: " + path, e);
            }
        }

        long eval(List<Object> keys, Object... args) {
            RScript script = redissonClient.getScript(StringCodec.INSTANCE);
            String current = sha != null ? sha : load(script);
            try {
                return evalSha(script, current, keys, args);
            } catch (RedisException e) {
                if (!isNoScript(e)) {
                    throw e;
                }
                log.warn("Redis 스크립트 캐시가 비어 한도 스크립트를 다시 올립니다");
                return evalSha(script, load(script), keys, args);
            }
        }

        private String load(RScript script) {
            String loaded = script.scriptLoad(source);
            sha = loaded;
            return loaded;
        }

        private long evalSha(RScript script, String digest, List<Object> keys, Object... args) {
            Long result = script.evalSha(RScript.Mode.READ_WRITE, digest, RScript.ReturnType.INTEGER, keys, args);
            return result;
        }
    }
}
