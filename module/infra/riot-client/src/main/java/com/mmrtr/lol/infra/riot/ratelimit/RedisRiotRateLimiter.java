package com.mmrtr.lol.infra.riot.ratelimit;

import com.mmrtr.lol.infra.riot.config.RiotAPIProperties;
import com.mmrtr.lol.support.riot.RiotCallPriority;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 * Redis Lua 로 버킷을 원자적으로 확인·증가하는 {@link RiotRateLimiter}. 키는 모두 {@code riot:rl:} 아래에 있다.
 *
 * <p>카운터는 재시작해도 지우지 않는다. 기동 시 초기화를 하지 않으므로 여러 인스턴스가 같은 예산을 나눠 쓴다.
 */
@Slf4j
@Component
public class RedisRiotRateLimiter implements RiotRateLimiter {

    private static final String PREFIX = "riot:rl:";
    private static final String LIMITS_KEY = PREFIX + "limits";
    /** 같은 한도라도 마지막으로 쓴 지 이만큼 지나면 다시 쓴다. Redis 가 limits 해시를 잃어도 복구된다. */
    private static final long LIMIT_REFRESH_MILLIS = 30_000;

    private final RedissonClient redissonClient;
    private final RiotAPIProperties.RateLimit config;
    private final LongSupplier nowMillisOverride;
    private final String appDefault;
    private final LuaScript acquireScript = new LuaScript("ratelimit/riot-acquire.lua");
    private final LuaScript pauseScript = new LuaScript("ratelimit/riot-pause.lua");
    /** 이 인스턴스가 필드별로 마지막에 쓴 한도와 그 시각. */
    private final Map<String, WrittenLimit> lastWrittenLimits = new ConcurrentHashMap<>();

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
    public long acquire(String routing, String method, RiotCallPriority priority) {
        return acquireScript.eval(List.of(),
                routing, method, String.valueOf(priority.level()), appDefault,
                String.valueOf(config.getSafetyRatio()), String.valueOf(config.getLookaheadSeconds()),
                String.valueOf(config.getReserveRatio()), nowArg());
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
        pauseScript.eval(List.of(PREFIX + "pause:" + bucket), String.valueOf(millis), nowArg());
    }

    @Override
    public long backlogMillis(String routing) {
        String horizon = redissonClient.<String>getBucket(PREFIX + "horizon:" + routing, StringCodec.INSTANCE).get();
        if (horizon == null) {
            return 0;
        }
        return Math.max(0, Long.parseLong(horizon) * 1000 - localNowMillis());
    }

    @Override
    public int userCallsPerSecond(String routing) {
        String learned = limits().get(appBucket(routing));
        return RateLimitWindow.callsPerSecond(
                RateLimitWindow.parse(learned != null ? learned : appDefault), config.getSafetyRatio());
    }

    /** 마지막에 쓴 값과 다르거나 쓴 지 {@link #LIMIT_REFRESH_MILLIS} 가 지났으면 HSET 한다. 필드별 확인·갱신은 원자적이다. */
    private void learn(String field, String header) {
        String limits = normalize(header);
        if (limits == null) {
            return;
        }
        lastWrittenLimits.compute(field, (key, written) -> {
            long now = localNowMillis();
            boolean changed = written == null || !written.limits().equals(limits);
            if (!changed && now - written.writtenAtMillis() <= LIMIT_REFRESH_MILLIS) {
                return written;
            }
            limits().fastPut(field, limits);
            if (changed) {
                log.info("Riot 한도 갱신 [{}] {}", field, limits);
            }
            return new WrittenLimit(limits, now);
        });
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

    /** 헤더를 "limit:W,..." 로 정규화한다. 유효한 창이 없으면 null. Lua 는 이 형식만 읽는다. */
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
