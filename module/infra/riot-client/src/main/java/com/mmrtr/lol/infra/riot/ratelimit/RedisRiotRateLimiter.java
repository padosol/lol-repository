package com.mmrtr.lol.infra.riot.ratelimit;

import com.mmrtr.lol.common.type.Platform;
import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import com.mmrtr.lol.support.riot.RiotMethod;
import com.mmrtr.lol.support.riot.RiotPermit;
import com.mmrtr.lol.support.riot.RiotPermitPort;
import com.mmrtr.lol.support.riot.RiotPermits;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
 * Redis ZSET 슬라이딩 창으로 버킷 한도를 지키는 {@link RiotPermitPort}·{@link RiotRateLimiter}. 키는 모두 {@code riot:rl:}
 * 아래에 있다.
 *
 * <p>규칙(버킷 × 창)마다 ZSET 하나에 허가된 호출의 토큰을 허가 시각으로 기록하고, {@link #complete} 가 Riot 한도
 * 규칙(app·method)의 기록만 응답 시각으로 옮긴다. 허가는 routing 마다 Lua 하나가 모든 규칙을 검사한 뒤 전부 통과할 때만
 * 모든 규칙에 기록한다. 한도는 Java 가 버킷별로 알고 인자로 넘긴다.
 *
 * <p>기록은 재시작해도 지우지 않는다. 기동 시 초기화를 하지 않으므로 여러 인스턴스가 같은 예산을 나눠 쓴다.
 */
@Slf4j
@Component
public class RedisRiotRateLimiter implements RiotRateLimiter, RiotPermitPort {

    private static final String PREFIX = "riot:rl:";
    private static final String LIMITS_KEY = PREFIX + "limits";
    /** 같은 한도라도 마지막으로 쓴 지 이만큼 지나면 다시 쓴다. Redis 가 limits 해시를 잃어도 복구된다. */
    private static final long LIMIT_REFRESH_MILLIS = 30_000;
    /** 백필 전용 규칙의 창(초). */
    private static final int BACKGROUND_WINDOW_SECONDS = 1;

    private final RedissonClient redissonClient;
    private final RiotAPIProperties.RateLimit config;
    private final LongSupplier nowMillisOverride;
    private final MeterRegistry meterRegistry;
    private final String appDefault;
    private final LuaScript permitScript = new LuaScript("ratelimit/riot-permit.lua");
    private final LuaScript completeScript = new LuaScript("ratelimit/riot-complete.lua");
    private final LuaScript pauseScript = new LuaScript("ratelimit/riot-pause.lua");
    /** 이 인스턴스가 필드별로 마지막에 쓴 한도와 그 시각. */
    private final Map<String, WrittenLimit> lastWrittenLimits = new ConcurrentHashMap<>();
    /** 버킷별로 아는 한도(정규화된 문자열, 배운 값이 없으면 null)와 그 값을 얻은 시각. */
    private final Map<String, CachedLimit> cachedLimits = new ConcurrentHashMap<>();

    @Autowired
    public RedisRiotRateLimiter(RedissonClient redissonClient, RiotAPIProperties properties,
                                MeterRegistry meterRegistry) {
        this(redissonClient, properties, null, meterRegistry);
    }

    RedisRiotRateLimiter(RedissonClient redissonClient, RiotAPIProperties properties) {
        this(redissonClient, properties, null, new SimpleMeterRegistry());
    }

    RedisRiotRateLimiter(RedissonClient redissonClient, RiotAPIProperties properties, LongSupplier nowMillisOverride) {
        this(redissonClient, properties, nowMillisOverride, new SimpleMeterRegistry());
    }

    /** nowMillisOverride 가 null 이면 Lua 가 Redis TIME 을 시계로 쓴다. */
    RedisRiotRateLimiter(RedissonClient redissonClient, RiotAPIProperties properties, LongSupplier nowMillisOverride,
                         MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.redissonClient = redissonClient;
        this.config = properties.getRateLimit();
        this.nowMillisOverride = nowMillisOverride;
        this.appDefault = normalize(config.getAppDefault());
        if (appDefault == null) {
            throw new IllegalArgumentException("riot.rate-limit.app-default 형식이 잘못되었습니다: " + config.getAppDefault());
        }
    }

    /**
     * counts 를 routing 별로 묶어 묶음마다 Lua 하나로 허가를 판단한다. REGIONAL 묶음이 먼저다. 한 묶음이라도 거절이면
     * 빈 값이고, 앞서 허가돼 기록된 묶음의 토큰은 지우지 않는다 (창을 빠질 때까지 예산을 쓴 것으로 센다).
     */
    @Override
    public Optional<RiotPermits> tryAcquire(String platform, RiotCallPriority priority,
                                            Map<RiotMethod, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            throw new IllegalArgumentException("요청할 호출이 없습니다");
        }
        counts.forEach((method, count) -> {
            if (count == null || count <= 0) {
                throw new IllegalArgumentException("건수는 1 이상이어야 합니다: " + method + "=" + count);
            }
        });
        Platform target = platformOf(platform);
        // routing 별 묶음. REGIONAL → PLATFORM 순서, 같은 routing 안에서는 RiotMethod 순서
        Map<String, Map<RiotMethod, Integer>> groups = new LinkedHashMap<>();
        for (RiotMethod.Routing kind : RiotMethod.Routing.values()) {
            Map<RiotMethod, Integer> sorted = new EnumMap<>(RiotMethod.class);
            counts.forEach((method, count) -> {
                if (method.routing() == kind) {
                    sorted.put(method, count);
                }
            });
            if (!sorted.isEmpty()) {
                groups.put(routingOf(target, kind), sorted);
            }
        }
        List<RiotPermit> granted = new ArrayList<>();
        for (Map.Entry<String, Map<RiotMethod, Integer>> group : groups.entrySet()) {
            String routing = group.getKey();
            String batchId = UUID.randomUUID().toString();
            List<PermitRule> rules = permitRules(routing, group.getValue().keySet(), priority);
            boolean ok = permitScript.eval(permitKeys(routing, group.getValue(), rules),
                    permitArgs(batchId, group.getValue(), priority, rules)) == 1;
            meterRegistry.counter("riot.permit.requests", "routing", routing,
                    "priority", priority.name(), "result", ok ? "granted" : "denied").increment();
            if (!ok) {
                return Optional.empty();
            }
            group.getValue().forEach((method, count) -> {
                for (int k = 1; k <= count; k++) {
                    granted.add(new RiotPermit(batchId + ":" + method.methodName() + ":" + k, platform, method,
                            priority));
                }
            });
        }
        return Optional.of(RiotPermits.of(granted));
    }

    /**
     * 허가된 token 의 기록을 허가 때와 같은 규칙 중 Riot 한도 규칙(낮춤 규칙 = app·method)에서만 지금 시각으로 옮긴다.
     * 응답 시각으로 옮기는 것은 Riot 이 센 도착 시각에 맞추려는 것이라, 우리가 정한 백필 전용 규칙은 허가 시각에 둔다.
     * 옮기면 백필이 초당 40 / (1 + 응답 지연) 회로 준다. 그 토큰이 없는 규칙은 Lua 가 건너뛴다.
     */
    @Override
    public void complete(RiotPermit permit) {
        String routing = routingOf(platformOf(permit.platform()), permit.method().routing());
        List<Object> keys = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        args.add(permit.token());
        args.add(nowArg());
        for (PermitRule pr : permitRules(routing, List.of(permit.method()), permit.priority())) {
            if (!pr.rule().lowered()) {
                continue;
            }
            keys.add(pr.rule().key());
            args.add(String.valueOf(pr.rule().windowMs()));
        }
        completeScript.eval(keys, args.toArray());
    }

    /** platform 이름(대소문자 무관). 모르는 이름이면 IllegalArgumentException. */
    private static Platform platformOf(String platform) {
        Platform target = Platform.valueOfName(platform);
        if (target == null) {
            throw new IllegalArgumentException("알 수 없는 platform: " + platform);
        }
        return target;
    }

    /** 호스트({@code https://asia.api.riotgames.com})의 첫 {@code .} 앞. {@code RiotCallRecorder} 가 보는 routing 과 같은 값이다. */
    static String routingOf(Platform platform, RiotMethod.Routing routing) {
        String host = routing == RiotMethod.Routing.REGIONAL ? platform.getRegionalHost() : platform.getPlatformHost();
        int scheme = host.indexOf("://");
        String bare = scheme < 0 ? host : host.substring(scheme + 3);
        int dot = bare.indexOf('.');
        return dot < 0 ? bare : bare.substring(0, dot);
    }

    private List<Object> permitKeys(String routing, Map<RiotMethod, Integer> group, List<PermitRule> rules) {
        List<Object> keys = new ArrayList<>();
        keys.add(PREFIX + "user:" + routing);
        keys.add(pauseKey(appBucket(routing)));
        group.keySet().forEach(m -> keys.add(pauseKey(methodBucket(routing, m.methodName()))));
        rules.forEach(pr -> keys.add(pr.rule().key()));
        return keys;
    }

    private Object[] permitArgs(String batchId, Map<RiotMethod, Integer> group, RiotCallPriority priority,
                                List<PermitRule> rules) {
        List<Object> args = new ArrayList<>();
        args.add(batchId);
        args.add(nowArg());
        args.add(String.valueOf(priority.level()));
        args.add(String.valueOf(ceilingRatio(priority)));
        args.add(String.valueOf(config.getUserActiveWindow().toMillis()));
        args.add(String.valueOf(1 + group.size()));
        args.add(String.valueOf(group.size()));
        group.forEach((method, count) -> {
            args.add(method.methodName());
            args.add(String.valueOf(count));
        });
        for (PermitRule pr : rules) {
            args.add(String.valueOf(pr.rule().cap()));
            args.add(String.valueOf(pr.rule().windowMs()));
            args.add(pr.rule().lowered() ? "1" : "0");
            args.add(pr.target());
        }
        return args.toArray();
    }

    /**
     * 묶음에 적용할 규칙. app 한도(배운 값, 없으면 app-default)와 method 한도(배운 값이 있을 때만)의 창마다 낮춤 규칙 하나씩이고,
     * BACKGROUND 면 백필 전용 1초 규칙을 더한다. 허가({@link #tryAcquire})와 기록 옮기기({@link #complete})가 같이 쓴다.
     */
    private List<PermitRule> permitRules(String routing, Collection<RiotMethod> methods, RiotCallPriority priority) {
        List<PermitRule> result = new ArrayList<>();
        String app = appBucket(routing);
        String appLimits = knownLimits(app);
        List<Rule> appRules = new ArrayList<>();
        addRules(appRules, app, appLimits != null ? appLimits : appDefault);
        appRules.forEach(r -> result.add(new PermitRule(r, "")));
        for (RiotMethod method : methods) {
            String bucket = methodBucket(routing, method.methodName());
            String limits = knownLimits(bucket);
            if (limits != null) {
                List<Rule> methodRules = new ArrayList<>();
                addRules(methodRules, bucket, limits);
                methodRules.forEach(r -> result.add(new PermitRule(r, method.methodName())));
            }
        }
        int backgroundMax = config.getBackgroundMaxPerSecond();
        if (priority == RiotCallPriority.BACKGROUND && backgroundMax > 0) {
            result.add(new PermitRule(new Rule(ruleKey(backgroundBucket(routing), BACKGROUND_WINDOW_SECONDS),
                    backgroundMax, BACKGROUND_WINDOW_SECONDS * 1000L, false), ""));
        }
        return result;
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

    /** 한도의 창마다 상한 max(1, floor(한도 × safetyRatio)) 인 낮춤 규칙을 더한다. */
    private void addRules(List<Rule> rules, String bucket, String limits) {
        for (RateLimitWindow window : RateLimitWindow.parse(limits)) {
            int cap = Math.max(1, (int) Math.floor(window.limit() * config.getSafetyRatio()));
            rules.add(new Rule(ruleKey(bucket, window.windowSeconds()), cap, window.windowSeconds() * 1000L, true));
        }
    }

    /** 유저 활동 중 낮춤 규칙의 상한에 곱할 비율. 설정이 [0, 1] 밖이면 그 끝으로 자른다 (상한을 넘겨 쓰지 않게). */
    private double ceilingRatio(RiotCallPriority priority) {
        double ratio = switch (priority) {
            case USER -> 1.0;
            case DEFERRED -> config.getDeferredCeilingRatio();
            case BACKGROUND -> config.getBackgroundCeilingRatio();
        };
        return Math.max(0.0, Math.min(1.0, ratio));
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

    /** target 이 빈 문자열이면 묶음의 모든 method 에 적용되는 규칙(app·백필). */
    private record PermitRule(Rule rule, String target) {
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
