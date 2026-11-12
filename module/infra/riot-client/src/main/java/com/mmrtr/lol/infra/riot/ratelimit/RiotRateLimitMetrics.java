package com.mmrtr.lol.infra.riot.ratelimit;

import com.mmrtr.lol.infra.riot.ratelimit.RedisRiotRateLimiter.MonitoredPause;
import com.mmrtr.lol.infra.riot.ratelimit.RedisRiotRateLimiter.MonitoredRule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis 에 있는 Riot 한도 상태를 1초마다 읽어 지표로 낸다. 남은 한도는 {@code cap - used} 다.
 *
 * <ul>
 *   <li>{@code riot.ratelimit.used{routing,bucket,window}}: 창 안에서 허가된 호출 수 (허가 Lua 가 세는 것과 같다)</li>
 *   <li>{@code riot.ratelimit.cap{routing,bucket,window}}: 그 규칙의 상한 (설정 한도 × 안전 비율, 백필은 초당 상한).
 *       유저 활동 중 DEFERRED·BACKGROUND 가 쓰는 낮춘 상한이 아니다</li>
 *   <li>{@code riot.ratelimit.pause.remaining{routing,bucket}}: 429 정지가 끝날 때까지 남은 초</li>
 * </ul>
 * bucket 은 {@code app}, method 이름, {@code background} 다. Redis 에 키가 있는 규칙·정지만 낸다 (한가한 routing 은 없다).
 * 여러 인스턴스가 같은 Redis 를 읽으므로 대시보드에서는 {@code max by} 로 묶는다.
 */
@Slf4j
@Component
public class RiotRateLimitMetrics {

    private static final String SCRIPT_PATH = "ratelimit/riot-usage.lua";

    private final RedissonClient redissonClient;
    private final LongSupplier nowMillisOverride;
    private final List<MonitoredRule> rules;
    private final List<MonitoredPause> pauses;
    private final List<Object> keys;
    private final String script;
    private final MultiGauge used;
    private final MultiGauge cap;
    private final MultiGauge pauseRemaining;
    private volatile boolean failing;

    @Autowired
    public RiotRateLimitMetrics(RedisRiotRateLimiter limiter, RedissonClient redissonClient,
                                MeterRegistry meterRegistry) {
        this(limiter, redissonClient, meterRegistry, null);
    }

    /** nowMillisOverride 가 null 이면 Lua 가 Redis TIME 을 시계로 쓴다 (한도 관리자와 같은 시계여야 한다). */
    RiotRateLimitMetrics(RedisRiotRateLimiter limiter, RedissonClient redissonClient, MeterRegistry meterRegistry,
                         LongSupplier nowMillisOverride) {
        this.redissonClient = redissonClient;
        this.nowMillisOverride = nowMillisOverride;
        this.rules = limiter.monitoredRules();
        this.pauses = limiter.monitoredPauses();
        this.keys = new ArrayList<>();
        rules.forEach(rule -> keys.add(rule.key()));
        pauses.forEach(pause -> keys.add(pause.key()));
        this.script = loadScript();
        this.used = MultiGauge.builder("riot.ratelimit.used")
                .description("Riot 한도 규칙의 창 안 허가 수").register(meterRegistry);
        this.cap = MultiGauge.builder("riot.ratelimit.cap")
                .description("Riot 한도 규칙의 상한").register(meterRegistry);
        this.pauseRemaining = MultiGauge.builder("riot.ratelimit.pause.remaining").baseUnit("seconds")
                .description("Riot 429 정지가 끝날 때까지 남은 시간").register(meterRegistry);
    }

    /** Redis 를 한 번 읽어 지표를 바꾼다. 읽지 못하면 지난 값을 둔 채 경고만 남긴다 (실패가 이어지면 처음 한 번). */
    @Scheduled(fixedDelay = 1000)
    public void refresh() {
        List<Long> values;
        try {
            values = read();
        } catch (RuntimeException e) {
            if (!failing) {
                log.warn("Riot 한도 지표를 읽지 못했습니다 (다시 읽힐 때까지 이 경고는 한 번만 남깁니다)", e);
            }
            failing = true;
            return;
        }
        failing = false;
        List<MultiGauge.Row<?>> usedRows = new ArrayList<>();
        List<MultiGauge.Row<?>> capRows = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            long count = values.get(i);
            if (count < 0) {
                continue;
            }
            MonitoredRule rule = rules.get(i);
            Tags tags = Tags.of("routing", rule.routing(), "bucket", rule.bucket(),
                    "window", String.valueOf(rule.windowSeconds()));
            usedRows.add(MultiGauge.Row.of(tags, count));
            capRows.add(MultiGauge.Row.of(tags, rule.cap()));
        }
        List<MultiGauge.Row<?>> pauseRows = new ArrayList<>();
        for (int i = 0; i < pauses.size(); i++) {
            long remainingMs = values.get(rules.size() + i);
            if (remainingMs < 0) {
                continue;
            }
            MonitoredPause pause = pauses.get(i);
            pauseRows.add(MultiGauge.Row.of(Tags.of("routing", pause.routing(), "bucket", pause.bucket()),
                    remainingMs / 1000.0));
        }
        used.register(usedRows, true);
        cap.register(capRows, true);
        pauseRemaining.register(pauseRows, true);
    }

    private List<Long> read() {
        List<Object> args = new ArrayList<>();
        args.add(nowMillisOverride != null ? String.valueOf(nowMillisOverride.getAsLong()) : "");
        args.add(String.valueOf(rules.size()));
        rules.forEach(rule -> args.add(String.valueOf(rule.windowMs())));
        List<Object> raw = redissonClient.getScript(StringCodec.INSTANCE)
                .eval(RScript.Mode.READ_ONLY, script, RScript.ReturnType.MULTI, keys, args.toArray());
        if (raw == null || raw.size() != keys.size()) {
            throw new IllegalStateException("Riot 한도 지표 응답 크기가 다릅니다: " + (raw == null ? null : raw.size()));
        }
        List<Long> values = new ArrayList<>(raw.size());
        raw.forEach(value -> values.add(((Number) value).longValue()));
        return values;
    }

    private static String loadScript() {
        try (InputStream in = RiotRateLimitMetrics.class.getClassLoader().getResourceAsStream(SCRIPT_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Lua 스크립트가 없습니다: " + SCRIPT_PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Lua 스크립트를 읽지 못했습니다: " + SCRIPT_PATH, e);
        }
    }
}
