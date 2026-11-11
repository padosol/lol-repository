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
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
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
 * <p>규칙(버킷 × 창)마다 ZSET 하나에 허가된 호출의 토큰을 허가 시각으로 기록한다. 허가는 routing 마다 Lua 하나가 모든
 * 규칙을 검사한 뒤 전부 통과할 때만 모든 규칙에 기록한다. 한도는 설정({@link RiotLimits})에서 읽어 인자로 넘기고, 같은 값을
 * routing 마다 따로 센다.
 *
 * <p>기록은 재시작해도 지우지 않는다. 기동 시 초기화를 하지 않으므로 여러 인스턴스가 같은 예산을 나눠 쓴다.
 */
@Slf4j
@Component
public class RedisRiotRateLimiter implements RiotRateLimiter, RiotPermitPort {

    private static final String PREFIX = "riot:rl:";
    /** 백필 전용 규칙의 창(초). */
    private static final int BACKGROUND_WINDOW_SECONDS = 1;

    private final RedissonClient redissonClient;
    private final RiotAPIProperties.RateLimit config;
    private final LongSupplier nowMillisOverride;
    private final MeterRegistry meterRegistry;
    private final RiotLimits limits;
    private final LuaScript permitScript = new LuaScript("ratelimit/riot-permit.lua");

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
        this.limits = RiotLimits.from(config);
        List<RiotMethod> unconfigured = limits.unconfigured();
        if (!unconfigured.isEmpty()) {
            log.info("Riot method 한도 설정 없음 (app 한도만 본다): {}",
                    unconfigured.stream().map(RiotMethod::methodName).toList());
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
            long code = permitScript.eval(permitKeys(routing, group.getValue(), rules),
                    permitArgs(batchId, group.getValue(), priority, rules));
            meterRegistry.counter("riot.permit.requests", "routing", routing,
                    "priority", priority.name(), "result", resultTag(code)).increment();
            if (code != 1) {
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

    /** riot-permit.lua 의 반환값 → 지표 result 태그. 모르는 값은 거절로 보고 unknown 이다. */
    static String resultTag(long code) {
        return switch ((int) code) {
            case 1 -> "granted";
            case -1 -> "app";
            case -2 -> "method";
            case -3 -> "paused";
            case -4 -> "background";
            default -> "unknown";
        };
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
            args.add(pr.kind().arg());
            args.add(pr.target());
        }
        return args.toArray();
    }

    /**
     * 묶음에 적용할 규칙. app 한도와 method 한도(설정에 있을 때만)의 창마다 낮춤 규칙 하나씩이고, BACKGROUND 면 백필 전용
     * 1초 규칙을 더한다. 순서는 app → method({@link RiotMethod} 순서) → 백필이다.
     */
    private List<PermitRule> permitRules(String routing, Collection<RiotMethod> methods, RiotCallPriority priority) {
        List<PermitRule> result = new ArrayList<>();
        windowRules(appBucket(routing), limits.app())
                .forEach(r -> result.add(new PermitRule(r, RuleKind.APP, "")));
        for (RiotMethod method : methods) {
            windowRules(methodBucket(routing, method.methodName()), limits.windowsOf(method))
                    .forEach(r -> result.add(new PermitRule(r, RuleKind.METHOD, method.methodName())));
        }
        int backgroundMax = config.getBackgroundMaxPerSecond();
        if (priority == RiotCallPriority.BACKGROUND && backgroundMax > 0) {
            result.add(new PermitRule(new Rule(ruleKey(backgroundBucket(routing), BACKGROUND_WINDOW_SECONDS),
                    backgroundMax, BACKGROUND_WINDOW_SECONDS * 1000L), RuleKind.BACKGROUND, ""));
        }
        return result;
    }

    /** 정지 키 하나를 duration 만료로 쓴다 (값은 "1"). 허가 Lua 는 키가 있는지만 본다. 나중 정지가 앞 정지를 덮어쓴다. */
    @Override
    public void pause(String routing, String methodOrNull, Duration duration) {
        long millis = duration.toMillis();
        if (millis <= 0) {
            return;
        }
        String bucket = methodOrNull == null ? appBucket(routing) : methodBucket(routing, methodOrNull);
        redissonClient.getBucket(pauseKey(bucket), StringCodec.INSTANCE).set("1", Duration.ofMillis(millis));
    }

    /** 규칙 ZSET 키. bucket 은 {@code {routing}|app}, {@code {routing}|m|{method}}, {@code {routing}|bg} 다. */
    static String ruleKey(String bucket, int windowSeconds) {
        return PREFIX + "w:" + bucket + ":" + windowSeconds;
    }

    /** 한도의 창마다 상한 max(1, floor(한도 × safetyRatio)) 인 낮춤 규칙. */
    private List<Rule> windowRules(String bucket, List<RateLimitWindow> windows) {
        List<Rule> rules = new ArrayList<>();
        for (RateLimitWindow window : windows) {
            int cap = Math.max(1, (int) Math.floor(window.limit() * config.getSafetyRatio()));
            rules.add(new Rule(ruleKey(bucket, window.windowSeconds()), cap, window.windowSeconds() * 1000L));
        }
        return rules;
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

    private String nowArg() {
        return nowMillisOverride != null ? String.valueOf(nowMillisOverride.getAsLong()) : "";
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

    private static boolean isNoScript(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("NOSCRIPT")) {
                return true;
            }
        }
        return false;
    }

    private record Rule(String key, int cap, long windowMs) {
    }

    /**
     * 규칙 종류. Lua 는 이 순서(app → method → 백필)로 검사해 막힌 종류를 반환값으로 알려 준다. 유저 활동 중 낮춤은
     * app·method 에만 한다.
     */
    private enum RuleKind {
        APP("app"), METHOD("method"), BACKGROUND("background");

        private final String arg;

        RuleKind(String arg) {
            this.arg = arg;
        }

        String arg() {
            return arg;
        }
    }

    /** target 은 method 규칙의 대상 method, 그 밖(app·백필)은 빈 문자열이다. */
    private record PermitRule(Rule rule, RuleKind kind, String target) {
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
